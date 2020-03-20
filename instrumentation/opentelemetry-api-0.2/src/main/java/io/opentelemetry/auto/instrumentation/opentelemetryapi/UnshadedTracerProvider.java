/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.opentelemetryapi;

import unshaded.io.opentelemetry.trace.Tracer;
import unshaded.io.opentelemetry.trace.TracerProvider;

public class UnshadedTracerProvider implements TracerProvider {

  @Override
  public Tracer get(final String instrumentationName) {
    return new UnshadedTracer(
        io.opentelemetry.OpenTelemetry.getTracerProvider().get(instrumentationName));
  }

  @Override
  public Tracer get(final String instrumentationName, final String instrumentationVersion) {
    return new UnshadedTracer(
        io.opentelemetry.OpenTelemetry.getTracerProvider()
            .get(instrumentationName, instrumentationVersion));
  }
}
