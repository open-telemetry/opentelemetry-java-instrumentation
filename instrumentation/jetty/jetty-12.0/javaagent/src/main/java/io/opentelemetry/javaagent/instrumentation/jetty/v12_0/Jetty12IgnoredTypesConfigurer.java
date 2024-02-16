/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(IgnoredTypesConfigurer.class)
public class Jetty12IgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
    // handling pipelined request sends HttpConnection instance (implements Runnable) to executor
    // while scope from the previous request is still active
    builder.ignoreTaskClass("org.eclipse.jetty.server.internal.HttpConnection");
  }
}
