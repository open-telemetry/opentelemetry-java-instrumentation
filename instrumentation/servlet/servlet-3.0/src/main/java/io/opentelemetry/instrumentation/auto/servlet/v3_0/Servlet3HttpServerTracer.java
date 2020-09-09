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

package io.opentelemetry.instrumentation.auto.servlet.v3_0;

import static io.opentelemetry.trace.TracingContextUtils.getSpan;

import io.grpc.Context;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3HttpServerTracer extends ServletHttpServerTracer<HttpServletResponse> {

  public static final Servlet3HttpServerTracer TRACER = new Servlet3HttpServerTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.servlet-3.0";
  }

  @Override
  protected Integer peerPort(HttpServletRequest connection) {
    return connection.getRemotePort();
  }

  @Override
  protected int responseStatus(HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }

  @Override
  public void endExceptionally(
      Span span, Throwable throwable, HttpServletResponse response, long timestamp) {
    if (response.isCommitted()) {
      super.endExceptionally(span, throwable, response, timestamp);
    } else {
      // passing null response to super, in order to capture as 500 / INTERNAL, due to servlet spec
      // https://javaee.github.io/servlet-spec/downloads/servlet-4.0/servlet-4_0_FINAL.pdf:
      // "If a servlet generates an error that is not handled by the error page mechanism as
      // described above, the container must ensure to send a response with status 500."
      super.endExceptionally(span, throwable, null, timestamp);
    }
  }

  @Override
  public void end(Span span, HttpServletResponse response, long timestamp) {
    super.end(span, response, timestamp);
  }

  public void onTimeout(Span span, long timeout) {
    span.setStatus(Status.DEADLINE_EXCEEDED);
    span.setAttribute("timeout", timeout);
    span.end();
  }

  /*
  Given request already has a context associated with it.
  As there should not be nested spans of kind SERVER, we should NOT create a new span here.

  But it may happen that there is no span in current Context or it is from a different trace.
  E.g. in case of async servlet request processing we create span for incoming request in one thread,
  but actual request continues processing happens in another thread.
  Depending on servlet container implementation, this processing may again arrive into this method.
  E.g. Jetty handles async requests in a way that calls HttpServlet.service method twice.

  In this case we have to put the span from the request into current context before continuing.
  */
  public static boolean needsRescoping(Context attachedContext) {
    return !sameTrace(getSpan(Context.current()), getSpan(attachedContext));
  }

  private static boolean sameTrace(Span oneSpan, Span otherSpan) {
    return oneSpan.getContext().getTraceId().equals(otherSpan.getContext().getTraceId());
  }
}
