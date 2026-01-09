/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.YamlDeclarativeConfigProperties;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;
import java.util.Map;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

/**
 * Allows access to the Javaagent distribution node, which cannot be accessed using the {@link
 * ConfigProvider} API (yet).
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public final class JavaagentDistributionAccessCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {

  // the entire configuration is copied from
  // https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/incubator/src/main/java/io/opentelemetry/sdk/extension/incubator/fileconfig/DeclarativeConfiguration.java#L66-L79
  // which is not public
  private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER;

  static {
    MAPPER =
        new ObjectMapper()
            // Create empty object instances for keys which are present but have null values
            .setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY));
    // Boxed primitives which are present but have null values should be set to null, rather than
    // empty instances
    MAPPER.configOverride(String.class).setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SET));
    MAPPER.configOverride(Integer.class).setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SET));
    MAPPER.configOverride(Double.class).setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SET));
    MAPPER.configOverride(Boolean.class).setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SET));
  }

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          Object distribution = model.getAdditionalProperties().get("distribution");
          if (distribution != null) {
            EarlyInitAgentConfig.setJavaagentConfig(
                toProperties(distribution).getStructured("javaagent", empty()));
          }
          return model;
        });
  }

  @NotNull
  private static YamlDeclarativeConfigProperties toProperties(Object distribution) {
    Map<String, Object> configurationMap =
        MAPPER.convertValue(distribution, new TypeReference<Map<String, Object>>() {});
    if (configurationMap == null) {
      configurationMap = Collections.emptyMap();
    }
    return YamlDeclarativeConfigProperties.create(
        configurationMap,
        ComponentLoader.forClassLoader(
            JavaagentDistributionAccessCustomizerProvider.class.getClassLoader()));
  }
}
