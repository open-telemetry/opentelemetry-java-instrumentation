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

package io.opentelemetry.instrumentation.auto.ratpack;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import ratpack.handling.Context;

public class RatpackTracer extends BaseTracer {
  public static final RatpackTracer TRACER = new RatpackTracer();

  public Span onContext(Span span, Context ctx) {
    String description = ctx.getPathBinding().getDescription();
    if (description == null || description.isEmpty()) {
      description = "/";
    } else if (!description.startsWith("/")) {
      description = "/" + description;
    }

    span.updateName(description);

    return span;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.ratpack-1.4";
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof Error && throwable.getCause() != null) {
      return throwable.getCause();
    } else {
      return throwable;
    }
  }
}
