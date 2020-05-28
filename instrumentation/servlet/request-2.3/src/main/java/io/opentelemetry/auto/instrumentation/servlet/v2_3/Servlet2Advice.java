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
package io.opentelemetry.auto.instrumentation.servlet.v2_3;

import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.typed.server.http.HttpServerSpanWithScope;
import java.lang.reflect.Method;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Argument;
import net.bytebuddy.asm.Advice.Origin;
import net.bytebuddy.asm.Advice.This;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;

public class Servlet2Advice {
  public static final Servlet2HttpServerTracer TRACER = new Servlet2HttpServerTracer();

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static HttpServerSpanWithScope onEnter(
      @This final Object servlet,
      @Origin final Method method,
      @Argument(0) final ServletRequest request,
      @Argument(value = 1, typing = Typing.DYNAMIC) final ServletResponse response) {

    if (!(request instanceof HttpServletRequest)) {
      return null;
    }

    final HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    // For use by HttpServletResponseInstrumentation:
    InstrumentationContext.get(HttpServletResponse.class, HttpServletRequest.class)
        .put((HttpServletResponse) response, httpServletRequest);

    return TRACER.startSpan(httpServletRequest, method, servlet.getClass().getName());

  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Enter final HttpServerSpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable) {
    if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
      TRACER.setPrincipal((HttpServletRequest) request);

      Integer responseStatus = InstrumentationContext
          .get(ServletResponse.class, Integer.class)
          .get(response);
      ResponseWithStatus responseWithStatus = new ResponseWithStatus((HttpServletResponse) response,
          responseStatus);

      if (throwable == null) {
        TRACER.end(spanWithScope, responseWithStatus);
      } else {
        TRACER.endExceptionally(spanWithScope, throwable, responseWithStatus);
      }
    }
  }
}
