/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.util.Collections;
import java.util.Map;

/**
 * Spring flavor of {@link io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider}
 * that tries to coerce types, because spring doesn't tell what the original type was.
 *
 * <p>The entire class is a copy of <a
 * href="https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/incubator/src/main/java/io/opentelemetry/sdk/extension/incubator/fileconfig/SdkConfigProvider.java">SdkConfigProvider</a>
 * which uses {@link SpringDeclarativeConfigProperties} instead of {@link
 * io.opentelemetry.sdk.extension.incubator.fileconfig.YamlDeclarativeConfigProperties}.
 */
final class SpringConfigProvider implements ConfigProvider {

  private final DeclarativeConfigProperties instrumentationConfig;

  private SpringConfigProvider(
      OpenTelemetryConfigurationModel model, ComponentLoader componentLoader) {
    DeclarativeConfigProperties configProperties = toConfigProperties(model, componentLoader);
    this.instrumentationConfig = configProperties.get("instrumentation/development");
  }

  private static DeclarativeConfigProperties toConfigProperties(
      OpenTelemetryConfigurationModel model, ComponentLoader componentLoader) {
    Map<String, Object> configurationMap =
        EmbeddedConfigFile.getObjectMapper()
            .convertValue(model, new TypeReference<Map<String, Object>>() {});
    if (configurationMap == null) {
      configurationMap = Collections.emptyMap();
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
    return new SpringConfigProvider(model, componentLoader);
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
