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

package io.opentelemetry.test.annotation;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;
import unshaded.io.opentelemetry.extensions.auto.annotations.WithSpan;
import unshaded.io.opentelemetry.trace.Span.Kind;

public class TracedWithSpan {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto");

  @WithSpan
  public String otel() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan("manualName")
  public String namedOtel() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan
  public String ignored() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan(kind = Kind.PRODUCER)
  public String oneOfAKind() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }
}
