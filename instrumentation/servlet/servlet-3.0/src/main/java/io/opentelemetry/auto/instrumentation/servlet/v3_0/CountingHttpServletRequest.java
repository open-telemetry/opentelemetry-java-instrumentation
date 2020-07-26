package io.opentelemetry.auto.instrumentation.servlet.v3_0;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * TODO(anuraaga): Implement counting, for now it just ensures startAsync is called with the wrapped
 * objects.
 */
public class CountingHttpServletRequest extends HttpServletRequestWrapper {

  private final HttpServletResponse response;

  public CountingHttpServletRequest(HttpServletRequest request,
      HttpServletResponse response) {
    super(request);
    this.response = response;
  }


  @Override
  public AsyncContext startAsync() throws IllegalStateException {
    return startAsync(this, response);
  }
}
