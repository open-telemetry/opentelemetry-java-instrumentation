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
import javax.annotation.Nullable;

/**
 * Spring implementation of {@link ConfigProvider} that tries to coerce types, because spring
 * doesn't tell what the original type was.
 */
final class SpringConfigProvider implements ConfigProvider {

  @Nullable private final DeclarativeConfigProperties instrumentationConfig;

  private SpringConfigProvider(
      OpenTelemetryConfigurationModel model, ComponentLoader componentLoader) {
    DeclarativeConfigProperties configProperties = toConfigProperties(model, componentLoader);
    this.instrumentationConfig = configProperties.getStructured("instrumentation/development");
  }

  static DeclarativeConfigProperties toConfigProperties(
      Object model, ComponentLoader componentLoader) {
    Map<String, Object> configurationMap =
        EmbeddedConfigFile.getObjectMapper()
            .convertValue(model, new TypeReference<Map<String, Object>>() {});
    if (configurationMap == null) {
      configurationMap = Collections.emptyMap();
    }
    return SpringYamlDeclarativeConfigProperties.create(configurationMap, componentLoader);
  }

  /**
   * Create a {@link SpringConfigProvider} from the {@code model}.
   *
   * @param model the configuration model
   * @return the {@link SpringConfigProvider}
   */
  static SpringConfigProvider create(OpenTelemetryConfigurationModel model) {
    return create(
        model, ComponentLoader.forClassLoader(SpringConfigProvider.class.getClassLoader()));
  }

  /**
   * Create a {@link SpringConfigProvider} from the {@code model}.
   *
   * @param model the configuration model
   * @param componentLoader the component loader used to load SPIs
   * @return the {@link SpringConfigProvider}
   */
  static SpringConfigProvider create(
      OpenTelemetryConfigurationModel model, ComponentLoader componentLoader) {
    return new SpringConfigProvider(model, componentLoader);
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return instrumentationConfig;
  }

  @Override
  public String toString() {
    return "SpringConfigProvider{" + "instrumentationConfig=" + instrumentationConfig + '}';
  }
}
