/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.core.v2_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(IgnoredTypesConfigurer.class)
public class SpringCoreIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
    // a Runnable task class that we don't need to touch
    builder
        .ignoreClass("org.springframework.util.ConcurrentLruCache$AddTask")
        .ignoreTaskClass("org.springframework.util.ConcurrentLruCache$AddTask");
  }
}
