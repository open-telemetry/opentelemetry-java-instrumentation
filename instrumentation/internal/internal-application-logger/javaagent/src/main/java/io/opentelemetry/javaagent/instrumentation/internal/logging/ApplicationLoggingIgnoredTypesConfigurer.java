/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.logging;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

@AutoService(IgnoredTypesConfigurer.class)
public final class ApplicationLoggingIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder) {
    builder.allowClass("org.slf4j.LoggerFactory");
    builder.allowClass("org.springframework.boot.SpringApplication");
    builder.allowClass("org.springframework.boot.logging.LoggingApplicationListener");
    builder.allowClass("org.springframework.boot.context.logging.LoggingApplicationListener");
  }
}
