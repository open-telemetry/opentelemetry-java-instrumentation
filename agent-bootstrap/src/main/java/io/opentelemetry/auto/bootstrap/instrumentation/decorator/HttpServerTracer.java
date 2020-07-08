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

package io.opentelemetry.auto.bootstrap.instrumentation.decorator;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;

// TODO In search for a better home package
public abstract class HttpServerTracer<REQUEST, CONNECTION, STORAGE>
    extends HttpServerTracerBase<REQUEST, CONNECTION, STORAGE> {
  protected final Tracer tracer;

  public HttpServerTracer() {
    tracer = OpenTelemetry.getTracerProvider().get(getInstrumentationName(), getVersion());
  }

  protected abstract String getInstrumentationName();

  protected abstract String getVersion();

  public Span startSpan(REQUEST request, CONNECTION connection, Method origin, String originType) {
    return startSpan(tracer, request, connection, origin, originType);
  }

  public Span startSpan(
      REQUEST request, CONNECTION connection, String spanName, String originType) {
    return startSpan(tracer, request, connection, spanName, originType);
  }

  public Span getCurrentSpan() {
    return tracer.getCurrentSpan();
  }
}
