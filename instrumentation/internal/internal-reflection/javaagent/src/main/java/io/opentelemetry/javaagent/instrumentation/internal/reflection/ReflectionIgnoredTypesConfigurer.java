/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.reflection;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(IgnoredTypesConfigurer.class)
public class ReflectionIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(ConfigProperties config, IgnoredTypesBuilder builder) {
    builder.allowClass("jdk.internal.reflect.Reflection");
    builder.allowClass("sun.reflect.Reflection");
    builder.allowClass("java.lang.Class");
  }
}
