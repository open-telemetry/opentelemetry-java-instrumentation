/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.opentelemetryapi.trace;

import io.opentelemetry.auto.bootstrap.ContextStore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unshaded.io.grpc.Context;
import unshaded.io.opentelemetry.internal.Obfuscated;
import unshaded.io.opentelemetry.trace.Tracer;
import unshaded.io.opentelemetry.trace.TracerProvider;

public class UnshadedTracerProvider implements TracerProvider, Obfuscated {

  private static final Logger log = LoggerFactory.getLogger(UnshadedTracerProvider.class);

  private static final AtomicBoolean messageAlreadyLogged = new AtomicBoolean();

  private final ContextStore<Context, io.grpc.Context> contextStore;
  private final TracerProvider originalTracerProvider;

  public UnshadedTracerProvider(
      ContextStore<Context, io.grpc.Context> contextStore, TracerProvider originalTracerProvider) {
    this.contextStore = contextStore;
    this.originalTracerProvider = originalTracerProvider;
  }

  @Override
  public Tracer get(final String instrumentationName) {
    return new UnshadedTracer(
        io.opentelemetry.OpenTelemetry.getTracerProvider().get(instrumentationName), contextStore);
  }

  @Override
  public Tracer get(final String instrumentationName, final String instrumentationVersion) {
    return new UnshadedTracer(
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
    return ((Obfuscated<?>) originalTracerProvider).unobfuscate();
  }
}
