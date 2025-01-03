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
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import javax.inject.Singleton;
import ratpack.exec.ExecInitializer;
import ratpack.exec.ExecInterceptor;
import ratpack.func.Action;
import ratpack.handling.Handler;
import ratpack.http.client.HttpClient;

public class OpenTelemetryModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(SpanExporter.class).toInstance(InMemorySpanExporter.create());
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

  @Provides
  @Singleton
  OpenTelemetry providesOpenTelemetry(SpanExporter spanExporter) {
    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
    return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
  }

  @Singleton
  @Provides
  HttpClient instrumentedHttpClient(RatpackClientTelemetry ratpackTracing) throws Exception {
    return ratpackTracing.instrument(HttpClient.of(Action.noop()));
  }

  @Singleton
  @Provides
  ExecInitializer ratpackExecInitializer(RatpackServerTelemetry ratpackTracing) {
    return ratpackTracing.getExecInitializer();
  }
}
