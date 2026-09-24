/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.util.Collections.emptyMap;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import java.util.Map;

/**
 * Adapts Spring-resolved configuration so each typed getter converts to the consumer's requested
 * type. Values resolved from properties, placeholders, and system-property overrides can be
 * Strings.
 *
 * <p>Runtime consumers share the provider built from the final SDK configuration. Bootstrap model
 * customizers use a separate view of their current model because the final provider is not yet
 * available and later customizers may still change the configuration.
 *
 * <p>The entire class is a copy of <a
 * href="https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/declarative-config/src/main/java/io/opentelemetry/sdk/autoconfigure/declarativeconfig/SdkConfigProvider.java">SdkConfigProvider</a>
 * which uses {@link SpringDeclarativeConfigProperties} instead of {@link
 * io.opentelemetry.sdk.autoconfigure.declarativeconfig.YamlDeclarativeConfigProperties}.
 */
final class SpringConfigProvider implements ConfigProvider {

  private final DeclarativeConfigProperties instrumentationConfig;

  private SpringConfigProvider(DeclarativeConfigProperties instrumentationConfig) {
    this.instrumentationConfig = instrumentationConfig;
  }

  private static DeclarativeConfigProperties toConfigProperties(
      OpenTelemetryConfigurationModel model, ComponentLoader componentLoader) {
    Map<String, Object> configurationMap =
        EmbeddedConfigFile.getObjectMapper()
            .convertValue(model, new TypeReference<Map<String, Object>>() {});
    if (configurationMap == null) {
      configurationMap = emptyMap();
    }
    return SpringDeclarativeConfigProperties.create(configurationMap, componentLoader);
  }

  /**
   * Create a {@link SpringConfigProvider} from the {@code model}.
   *
   * @param model the configuration model
   * @param componentLoader the component loader
   * @return the {@link SpringConfigProvider}
   */
  static SpringConfigProvider create(
      OpenTelemetryConfigurationModel model, ComponentLoader componentLoader) {
    DeclarativeConfigProperties configProperties = toConfigProperties(model, componentLoader);
    return new SpringConfigProvider(configProperties.get("instrumentation/development"));
  }

  static SpringConfigProvider create(DeclarativeConfigProperties instrumentationConfig) {
    return new SpringConfigProvider(
        SpringDeclarativeConfigProperties.create(
            DeclarativeConfigProperties.toMap(instrumentationConfig),
            instrumentationConfig.getComponentLoader()));
  }

  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return instrumentationConfig;
  }

  @Override
  public String toString() {
    return "SpringConfigProvider{instrumentationConfig=" + instrumentationConfig + '}';
  }
}
