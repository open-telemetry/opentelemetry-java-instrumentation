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
import java.util.HashMap;
import java.util.Map;
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
          return mapper.convertValue(javaagent, AgentDistributionConfig.class);
        } catch (IllegalArgumentException e) {
          logger.log(WARNING, "Failed to parse distribution.javaagent configuration", e);
        }
      }
    }

    return AgentDistributionConfig.create();
  }
}
