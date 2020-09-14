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

package io.opentelemetry.instrumentation.auto.jaxrs.v2_0;

import static io.opentelemetry.instrumentation.auto.jaxrs.v2_0.JaxRsAnnotationsTracer.TRACER;

import io.opentelemetry.trace.Span;
import java.util.function.BiFunction;

public class CompletionStageFinishCallback<T> implements BiFunction<T, Throwable, T> {
  private final Span span;

  public CompletionStageFinishCallback(Span span) {
    this.span = span;
  }

  @Override
  public T apply(T result, Throwable throwable) {
    if (throwable == null) {
      TRACER.end(span);
    } else {
      TRACER.endExceptionally(span, throwable);
    }
    return result;
  }
}
