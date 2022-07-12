/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quartz.v2_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(IgnoredTypesConfigurer.class)
public class QuartzIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(ConfigProperties config, IgnoredTypesBuilder builder) {
    // Quartz executes jobs themselves in a synchronous way, there's no reason to propagate context
    // between its scheduler threads.
    builder.ignoreTaskClass("org.quartz");
  }
}
