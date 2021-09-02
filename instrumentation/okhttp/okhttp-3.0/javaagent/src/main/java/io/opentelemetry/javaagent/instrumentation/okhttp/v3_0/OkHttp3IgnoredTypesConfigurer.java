/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

@AutoService(IgnoredTypesConfigurer.class)
public class OkHttp3IgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(Config config, IgnoredTypesBuilder builder) {
    // OkHttp task runner is a lazily-initialized shared pool of continuously running threads
    // similar to an event loop. The submitted tasks themselves should already be
    // instrumented to allow async propagation.
    builder.ignoreTaskClass("okhttp3.internal.concurrent.TaskRunner");
    // ConnectionPool constructor creates an anonymous Runnable for cleanup
    builder
        .ignoreTaskClass("okhttp3.ConnectionPool")
        .ignoreTaskClass("okhttp3.internal.connection.RealConnectionPool");
  }
}
