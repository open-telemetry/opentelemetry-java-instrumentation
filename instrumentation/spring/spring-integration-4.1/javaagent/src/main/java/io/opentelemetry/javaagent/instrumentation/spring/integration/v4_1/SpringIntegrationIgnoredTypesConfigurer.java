/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(IgnoredTypesConfigurer.class)
public class SpringIntegrationIgnoredTypesConfigurer implements IgnoredTypesConfigurer {
  @Override
  public void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
    // we don't instrument any messaging classes
    builder.ignoreClass("org.springframework.messaging");
  }
}
