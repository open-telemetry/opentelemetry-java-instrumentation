package datadog.trace.instrumentation.servlet2;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * SpanDecorator to decorate span at different stages in filter processing (before
 * filterChain.doFilter(), after and if exception is thrown).
 *
 * <p>Taken from
 * https://raw.githubusercontent.com/opentracing-contrib/java-web-servlet-filter/v0.1.0/opentracing-web-servlet-filter/src/main/java/io/opentracing/contrib/web/servlet/filter/ServletFilterSpanDecorator.java
 * and removed async and status code stuff to be Servlet 2.x compatible.
 *
 * @author Pavol Loffay
 */
public interface ServletFilterSpanDecorator {

  /**
   * Decorate span before {@link javax.servlet.Filter#doFilter(ServletRequest, ServletResponse,
   * FilterChain)} is called. This is called right after span in created. Span is already present in
   * request attributes with name {@link TracingFilter#SERVER_SPAN_CONTEXT}.
   *
   * @param httpServletRequest request
   * @param span span to decorate
   */
  void onRequest(HttpServletRequest httpServletRequest, Span span);

  /**
   * Decorate span after {@link javax.servlet.Filter#doFilter(ServletRequest, ServletResponse,
   * FilterChain)}.
   *
   * @param httpServletRequest request
   * @param httpServletResponse response
   * @param span span to decorate
   */
  void onResponse(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Span span);

  /**
   * Decorate span when an exception is thrown during processing in {@link
   * javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)}.
   *
   * @param httpServletRequest request
   * @param exception exception
   * @param span span to decorate
   */
  void onError(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      Throwable exception,
      Span span);

  /**
   * Decorate span on asynchronous request timeout.
   *
   * @param httpServletRequest request
   * @param httpServletResponse response
   * @param timeout timeout
   * @param span span to decorate
   */
  void onTimeout(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      long timeout,
      Span span);

  /**
   * Adds standard tags to span. {@link Tags#HTTP_URL}, {@link Tags#HTTP_STATUS}, {@link
   * Tags#HTTP_METHOD} and {@link Tags#COMPONENT}. If an exception during {@link
   * javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)} is thrown tag
   * {@link Tags#ERROR} is added and {@link Tags#HTTP_STATUS} not because at this point it is not
   * known.
   */
  ServletFilterSpanDecorator STANDARD_TAGS =
      new ServletFilterSpanDecorator() {
        @Override
        public void onRequest(final HttpServletRequest httpServletRequest, final Span span) {
          Tags.COMPONENT.set(span, "java-web-servlet");

          Tags.HTTP_METHOD.set(span, httpServletRequest.getMethod());
          //without query params
          Tags.HTTP_URL.set(span, httpServletRequest.getRequestURL().toString());
        }

        @Override
        public void onResponse(
            final HttpServletRequest httpServletRequest,
            final HttpServletResponse httpServletResponse,
            final Span span) {}

        @Override
        public void onError(
            final HttpServletRequest httpServletRequest,
            final HttpServletResponse httpServletResponse,
            final Throwable exception,
            final Span span) {
          Tags.ERROR.set(span, Boolean.TRUE);
          span.log(logsForException(exception));
        }

        @Override
        public void onTimeout(
            final HttpServletRequest httpServletRequest,
            final HttpServletResponse httpServletResponse,
            final long timeout,
            final Span span) {
          Tags.ERROR.set(span, Boolean.TRUE);

          final Map<String, Object> timeoutLogs = new HashMap<>();
          timeoutLogs.put("event", Tags.ERROR.getKey());
          timeoutLogs.put("message", "timeout");
          timeoutLogs.put("timeout", timeout);
        }

        private Map<String, String> logsForException(final Throwable throwable) {
          final Map<String, String> errorLog = new HashMap<>(3);
          errorLog.put("event", Tags.ERROR.getKey());

          final String message =
              throwable.getCause() != null
                  ? throwable.getCause().getMessage()
                  : throwable.getMessage();
          if (message != null) {
            errorLog.put("message", message);
          }
          final StringWriter sw = new StringWriter();
          throwable.printStackTrace(new PrintWriter(sw));
          errorLog.put("stack", sw.toString());

          return errorLog;
        }
      };
}
