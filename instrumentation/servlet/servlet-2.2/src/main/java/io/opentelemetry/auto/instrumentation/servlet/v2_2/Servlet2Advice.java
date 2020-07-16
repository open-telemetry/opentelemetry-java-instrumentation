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

package io.opentelemetry.auto.instrumentation.servlet.v2_2;

import static io.opentelemetry.auto.instrumentation.servlet.v2_2.Servlet2HttpServerTracer.TRACER;

import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class Servlet2Advice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Origin final Method method,
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(value = 1, typing = Assigner.Typing.DYNAMIC) final ServletResponse response,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }

    final HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    if (TRACER.getServerContext(httpServletRequest) != null) {
      return;
    }

    span = TRACER.startSpan(httpServletRequest, httpServletRequest, method);
    scope = TRACER.startScope(span, httpServletRequest);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Thrown final Throwable throwable,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();

    TRACER.setPrincipal(span, (HttpServletRequest) request);

    Integer responseStatus =
        InstrumentationContext.get(ServletResponse.class, Integer.class).get(response);

    if (throwable == null) {
      TRACER.end(span, responseStatus);
    } else {
      TRACER.endExceptionally(span, throwable, responseStatus);
    }
  }
}
