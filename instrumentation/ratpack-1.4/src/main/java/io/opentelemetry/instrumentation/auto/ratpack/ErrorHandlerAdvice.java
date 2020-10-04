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

import static io.opentelemetry.instrumentation.auto.ratpack.RatpackTracer.TRACER;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.util.Optional;
import net.bytebuddy.asm.Advice;
import ratpack.handling.Context;

public class ErrorHandlerAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void captureThrowable(
      @Advice.Argument(0) Context ctx, @Advice.Argument(1) Throwable throwable) {
    Optional<Span> span = ctx.maybeGet(Span.class);
    if (span.isPresent()) {
      // TODO this emulates old behaviour of BaseDecorator. Has to review
      span.get().setStatus(Status.ERROR);
      TRACER.addThrowable(span.get(), throwable);
    }
  }
}
