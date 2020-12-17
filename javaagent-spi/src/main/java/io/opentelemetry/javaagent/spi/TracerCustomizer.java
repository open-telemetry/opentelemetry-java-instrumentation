/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerManagement;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.config.TraceConfig;

/**
 * A service provider to allow programmatic customization of the tracing configuration. It will be
 * executed after the SDK has initialized a {@link SdkTracerProvider}. This means that not only can
 * the provided {@link SdkTracerProvider} be configured e.g. by calling {@link
 * SdkTracerProvider#updateActiveTraceConfig(TraceConfig)}, but static methods on {@link
 * io.opentelemetry.api.OpenTelemetry}, e.g., {@link
 * io.opentelemetry.api.OpenTelemetry#setGlobalPropagators(ContextPropagators)} can be used as well.
 *
 * <p>An implementation of {@link TracerCustomizer} can either be provided as part of an initializer
 * JAR, using the {@code otel.initializer.jar} property or can be included in the same JAR as the
 * agent in a redistribution.
 */
public interface TracerCustomizer {

  /** Callback executed after the initial {@link SdkTracerProvider} has been initialized. */
  void configure(SdkTracerManagement tracerManagement);
}
