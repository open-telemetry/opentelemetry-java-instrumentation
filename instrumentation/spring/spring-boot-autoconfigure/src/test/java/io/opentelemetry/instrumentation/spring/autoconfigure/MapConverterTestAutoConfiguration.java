/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.MapConverter;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Unconditionally create {@link MapConverter} bean, because the tests don't evaluate the
 * ConditionalOnBean annotation correctly.
 */
@Configuration
public class MapConverterTestAutoConfiguration {

  @Bean
  @ConfigurationPropertiesBinding
  public MapConverter mapConverter() {
    return new MapConverter();
  }
}
