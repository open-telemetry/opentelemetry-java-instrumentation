/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rxjava.v2_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

@AutoService(IgnoredTypesConfigurer.class)
public class RxJava2IgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(Config config, IgnoredTypesBuilder builder) {
    // ScheduledRunnable is a wrapper around a Runnable and doesn't itself need context.
    builder.ignoreTaskClass("io.reactivex.internal.schedulers.ScheduledRunnable");
  }
}
