package io.opentelemetry.auto.instrumentation.servlet.v3_0;

import io.opentelemetry.auto.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.auto.typed.server.http.HttpServerSpan;
import io.opentelemetry.trace.Status;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3HttpServerTracer extends ServletHttpServerTracer<HttpServletResponse> {

  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.servlet-3.0";
  }

  @Override
  protected Integer peerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  protected int status(HttpServletResponse response) {
    return response.getStatus();
  }

  public void onTimeout(HttpServerSpan span, long timeout) {
    span.setStatus(Status.DEADLINE_EXCEEDED);
    span.setAttribute("timeout", timeout); //TODO not defined
    span.end();
  }
}
