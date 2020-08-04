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

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.instrumentation.auto.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Iterator;
import java.util.List;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpServerTestAdvice {

  // needs to be public otherwise IllegalAccessError from inlined advice below
  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto");

  // needs to be public otherwise IllegalAccessError from inlined advice below
  public static final Logger log = LoggerFactory.getLogger(HttpServerTestAdvice.class);

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

      List<StackTraceElement[]> location =
          ExecutorInstrumentationUtils.THREAD_PROPAGATION_LOCATIONS.get(Context.current());
      if (location != null) {
        StringBuilder sb = new StringBuilder();
        Iterator<StackTraceElement[]> i = location.iterator();
        while (i.hasNext()) {
          for (StackTraceElement ste : i.next()) {
            sb.append("\n");
            sb.append(ste);
          }
          if (i.hasNext()) {
            sb.append("\nwhich was propagated from:");
          }
        }
        log.error("a context leak was detected. it was propagated from:{}", sb);
      }

      Span span = TRACER.spanBuilder("TEST_SPAN").startSpan();
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
