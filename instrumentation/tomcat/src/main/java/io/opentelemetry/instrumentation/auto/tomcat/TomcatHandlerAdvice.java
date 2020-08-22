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

package io.opentelemetry.instrumentation.auto.tomcat;

import static io.opentelemetry.instrumentation.auto.tomcat.TomcatHttpServerTracer.TRACER;

import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public class TomcatHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Origin Method method,
      @Advice.This Object source,
      @Advice.Argument(value = 0) Request request,
      @Advice.Argument(value = 1) Response response,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    Context attachedContext = TRACER.getServerContext(request);
    if (attachedContext != null) {
      // We are inside nested handler, don't create new span
      return;
    }

    span = TRACER.startSpan(request, request, method);
    scope = TRACER.startScope(span, request);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(value = 0) Request request,
      @Advice.Argument(value = 1) Response response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();

    if (span == null) {
      // an existing span was found
      return;
    }

    if (throwable != null) {
      TRACER.endExceptionally(span, throwable, response);
      return;
    }

    TRACER.end(span, response);
  }
}
