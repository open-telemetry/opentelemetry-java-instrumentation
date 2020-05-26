package io.opentelemetry.auto.instrumentation.servlet.v3_0;

import io.opentelemetry.auto.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicBoolean;
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

  @Override
  protected void onResponse(HttpServletRequest request, HttpServletResponse response,
      Throwable throwable, Span span) {
    final AtomicBoolean responseHandled = new AtomicBoolean(false);

    //In case of async servlets wait for the actual response to be ready
    if (request.isAsyncStarted()) {
      try {
        request.getAsyncContext()
            .addListener(new TagSettingAsyncListener(responseHandled, span, this));
      } catch (final IllegalStateException e) {
        // org.eclipse.jetty.server.Request may throw an exception here if request became
        // finished after check above. We just ignore that exception and move on.
      }
    }

    // Check again in case the request finished before adding the listener.
    if (!request.isAsyncStarted()) {
      onResponse(response, throwable, span, responseHandled);
    }
  }

  public void onResponse(HttpServletResponse response, Throwable throwable, Span span,
      AtomicBoolean responseHandled) {
    if (responseHandled.compareAndSet(false, true)) {
      super.onResponse(response, throwable, span);
    }
  }
}
