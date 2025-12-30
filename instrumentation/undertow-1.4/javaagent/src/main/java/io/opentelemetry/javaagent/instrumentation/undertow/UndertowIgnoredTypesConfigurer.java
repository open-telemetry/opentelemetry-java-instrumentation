/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

@AutoService(IgnoredTypesConfigurer.class)
public class UndertowIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder) {
    // When http pipelining is used HttpReadListener is passed to another worker thread to start
    // processing next request when the context from the old request is still active. Prevent
    // propagating context from the old request to the new one.
    builder.ignoreTaskClass("io.undertow.server.protocol.http.HttpReadListener");
  }
}
