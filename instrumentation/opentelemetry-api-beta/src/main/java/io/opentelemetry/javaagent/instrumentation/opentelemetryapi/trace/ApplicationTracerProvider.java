/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import application.io.grpc.Context;
import application.io.opentelemetry.internal.Obfuscated;
import application.io.opentelemetry.trace.Tracer;
import application.io.opentelemetry.trace.TracerProvider;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationTracerProvider implements TracerProvider, Obfuscated {

  private static final Logger log = LoggerFactory.getLogger(ApplicationTracerProvider.class);

  private static final AtomicBoolean messageAlreadyLogged = new AtomicBoolean();

  private final ContextStore<Context, io.grpc.Context> contextStore;
  private final TracerProvider applicationOriginalTracerProvider;

  public ApplicationTracerProvider(
      ContextStore<Context, io.grpc.Context> contextStore,
      TracerProvider applicationOriginalTracerProvider) {
    this.contextStore = contextStore;
    this.applicationOriginalTracerProvider = applicationOriginalTracerProvider;
  }

  @Override
  public Tracer get(String instrumentationName) {
    return new ApplicationTracer(
        io.opentelemetry.OpenTelemetry.getTracer(instrumentationName), contextStore);
  }

  @Override
  public Tracer get(String instrumentationName, String instrumentationVersion) {
    return new ApplicationTracer(
        io.opentelemetry.OpenTelemetry.getTracerProvider()
            .get(instrumentationName, instrumentationVersion),
        contextStore);
  }

  // this is called by OpenTelemetrySdk, which expects to get back a real TracerProviderSdk
  @Override
  public Object unobfuscate() {
    if (!messageAlreadyLogged.getAndSet(true)) {
      String message =
          "direct usage of the OpenTelemetry SDK, e.g. using OpenTelemetrySdk.getTracerProvider()"
              + " instead of OpenTelemetry.getTracerProvider(), is not supported when running agent"
              + " (see https://github.com/open-telemetry/opentelemetry-java-instrumentation#troubleshooting"
              + " for how to run with debug logging, which will log stack trace with this message)";
      if (log.isDebugEnabled()) {
        log.debug(message, new Exception("stack trace"));
      } else {
        log.info(message);
      }
    }
    return ((Obfuscated<?>) applicationOriginalTracerProvider).unobfuscate();
  }
}
