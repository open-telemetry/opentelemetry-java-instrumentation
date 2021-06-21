/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

@AutoService(IgnoredTypesConfigurer.class)
public class SpringIntegrationIgnoredTypesConfigurer implements IgnoredTypesConfigurer {
  @Override
  public void configure(Config config, IgnoredTypesBuilder builder) {
    // we don't instrument any messaging classes
    builder.ignoreClass("org.springframework.messaging");
  }
}
