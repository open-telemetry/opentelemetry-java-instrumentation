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
package io.opentelemetry.auto.test.base;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import net.bytebuddy.asm.Advice;

public abstract class HttpServerTestAdvice {

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto");

  /**
   * This advice should be applied at the root of a http server request to validate the
   * instrumentation correctly ignores other traces.
   */
  public static class ServerEntryAdvice {
    @Advice.OnMethodEnter
    public static SpanWithScope methodEnter() {
      if (!HttpServerTest.ENABLE_TEST_ADVICE.get()) {
        // Skip if not running the HttpServerTest.
        return null;
      }
      final Span span = TRACER.spanBuilder("TEST_SPAN").startSpan();
      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Enter final SpanWithScope spanWithScope) {
      if (spanWithScope != null) {
        spanWithScope.getSpan().end();
        spanWithScope.closeScope();
      }
    }
  }
}
