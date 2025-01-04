/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackClientTelemetry;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import javax.inject.Singleton;
import org.junit.jupiter.api.extension.RegisterExtension;
import ratpack.exec.ExecInitializer;
import ratpack.exec.ExecInterceptor;
import ratpack.handling.Handler;
import ratpack.http.client.HttpClient;

public class OpenTelemetryModule extends AbstractModule {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected void configure() {
    bind(OpenTelemetry.class).toInstance(testing.getOpenTelemetry());
  }

  @Singleton
  @Provides
  RatpackClientTelemetry ratpackClientTelemetry(OpenTelemetry openTelemetry) {
    return RatpackClientTelemetry.create(openTelemetry);
  }

  @Singleton
  @Provides
  RatpackServerTelemetry ratpackServerTelemetry(OpenTelemetry openTelemetry) {
    return RatpackServerTelemetry.create(openTelemetry);
  }

  @Singleton
  @Provides
  Handler ratpackServerHandler(RatpackServerTelemetry ratpackTracing) {
    return ratpackTracing.getHandler();
  }

  @Singleton
  @Provides
  ExecInterceptor ratpackExecInterceptor(RatpackServerTelemetry ratpackTracing) {
    return ratpackTracing.getExecInterceptor();
  }

  @Singleton
  @Provides
  HttpClient instrumentedHttpClient(RatpackClientTelemetry ratpackTracing) throws Exception {
    return ratpackTracing.instrument(HttpClient.of(spec -> {}));
  }

  @Singleton
  @Provides
  ExecInitializer ratpackExecInitializer(RatpackServerTelemetry ratpackTracing) {
    return ratpackTracing.getExecInitializer();
  }
}
