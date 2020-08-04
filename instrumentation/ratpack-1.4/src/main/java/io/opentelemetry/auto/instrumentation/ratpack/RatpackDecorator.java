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

package io.opentelemetry.auto.instrumentation.ratpack;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.library.api.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import ratpack.handling.Context;

public class RatpackDecorator extends BaseDecorator {
  public static final RatpackDecorator DECORATE = new RatpackDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.ratpack-1.4");

  public Span onContext(final Span span, final Context ctx) {
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
  public Span onError(final Span span, final Throwable throwable) {
    // Attempt to unwrap ratpack.handling.internal.HandlerException without direct reference.
    if (throwable instanceof Error && throwable.getCause() != null) {
      return super.onError(span, throwable.getCause());
    }
    return super.onError(span, throwable);
  }
}
