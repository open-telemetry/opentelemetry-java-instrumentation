/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.logging.Level.WARNING;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.config.internal.DeclarativeConfigV3Preview;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.DistributionModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Allows access to the Javaagent distribution node, which cannot be accessed using the {@link
 * ConfigProvider} API.
 */
@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public final class JavaagentDistributionAccessCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {

  private static final Logger logger =
      Logger.getLogger(JavaagentDistributionAccessCustomizerProvider.class.getName());

  private static final ObjectMapper mapper =
      new ObjectMapper()
          .addHandler(
              new DeserializationProblemHandler() {
                @Override
                public boolean handleUnknownProperty(
                    DeserializationContext ctxt,
                    JsonParser p,
                    JsonDeserializer<?> deserializer,
                    Object beanOrClass,
                    String propertyName)
                    throws IOException {
                  logger.warning("Unknown distribution.javaagent property: " + propertyName);
                  p.skipChildren();
                  return true;
                }
              });

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          boolean isV3Preview = DeclarativeConfigV3Preview.isEnabled(model);
          AgentDistributionConfig distributionConfig =
              parseConfig(model.getDistribution(), isV3Preview);
          AgentDistributionConfig.set(distributionConfig);
          return model;
        });
  }

  private static AgentDistributionConfig parseConfig(
      @Nullable DistributionModel distribution, boolean v3Preview) {

    // to be removed for 3.0.0
    // set 'distribution.javaagent.indy/development' to 'true' for v3 preview
    if (v3Preview) {
      // creating distribution.javaagent is required to add indy/development to it
      if (distribution == null) {
        distribution = new DistributionModel();
      }
      Object javaagent = distribution.getExtensionProperties().get("javaagent");
      Map<String, Object> javaagentProperties = new HashMap<>();
      if (javaagent != null) {
        javaagentProperties.putAll(
            mapper.convertValue(javaagent, new TypeReference<Map<String, Object>>() {}));
      }
      // when v3 preview is enabled, force indy enabled
      javaagentProperties.put("indy/development", true);
      distribution.setExtensionProperty("javaagent", javaagentProperties);
    }

    if (distribution != null) {
      Object javaagent = distribution.getExtensionProperties().get("javaagent");
      if (javaagent != null) {
        try {
          Map<String, Object> javaagentProperties =
              mapper.convertValue(javaagent, new TypeReference<Map<String, Object>>() {});
          migrateInstrumentationSelectors(javaagentProperties, v3Preview);
          return mapper.convertValue(javaagentProperties, AgentDistributionConfig.class);
        } catch (IllegalArgumentException e) {
          logger.log(WARNING, "Failed to parse distribution.javaagent configuration", e);
        }
      }
    }

    return AgentDistributionConfig.create();
  }

  private static void migrateInstrumentationSelectors(
      Map<String, Object> javaagentProperties, boolean v3Preview) {
    Object instrumentation = javaagentProperties.get("instrumentation");
    if (instrumentation == null) {
      return;
    }

    Map<String, Object> instrumentationProperties =
        mapper.convertValue(instrumentation, new TypeReference<Map<String, Object>>() {});
    List<String> enabled = getSelectorList(instrumentationProperties, "enabled");
    List<String> disabled = getSelectorList(instrumentationProperties, "disabled");
    Set<String> canonicalSelectors = new HashSet<>();
    addCanonicalSelectors(canonicalSelectors, enabled);
    addCanonicalSelectors(canonicalSelectors, disabled);
    instrumentationProperties.put(
        "enabled",
        migrateSelectorList(
            enabled,
            canonicalSelectors,
            v3Preview,
            "distribution.javaagent.instrumentation.enabled"));
    instrumentationProperties.put(
        "disabled",
        migrateSelectorList(
            disabled,
            canonicalSelectors,
            v3Preview,
            "distribution.javaagent.instrumentation.disabled"));
    javaagentProperties.put("instrumentation", instrumentationProperties);
  }

  private static List<String> getSelectorList(
      Map<String, Object> instrumentationProperties, String name) {
    Object selectors = instrumentationProperties.get(name);
    return selectors == null
        ? new ArrayList<>()
        : mapper.convertValue(selectors, new TypeReference<List<String>>() {});
  }

  private static void addCanonicalSelectors(Set<String> canonicalSelectors, List<String> selectors) {
    for (String selector : selectors) {
      if (!selector.contains(".")) {
        canonicalSelectors.add(selector);
      }
    }
  }

  private static List<String> migrateSelectorList(
      List<String> selectors,
      Set<String> canonicalSelectors,
      boolean v3Preview,
      String path) {
    List<String> result = new ArrayList<>();
    Set<String> warnedSelectors = new HashSet<>();
    for (String selector : selectors) {
      if (!selector.contains(".")) {
        result.add(selector);
        continue;
      }

      String replacement = selector.replace('.', '_');
      if (v3Preview) {
        continue;
      }
      if (warnedSelectors.add(selector)) {
        logger.warning(
            "Declarative configuration entry '"
                + selector
                + "' in '"
                + path
                + "' is deprecated; use '"
                + replacement
                + "' instead. The deprecated entry will be removed in 3.0.");
      }
      if (!canonicalSelectors.contains(replacement)) {
        result.add(replacement);
      }
    }
    return result;
  }
}
