package datadog.trace.instrumentation.okhttp3;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Connection;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Span decorator to add tags, logs and operation name.
 *
 * @author Pavol Loffay
 */
public interface OkHttpClientSpanDecorator {

  /**
   * Decorate span before a request is made.
   *
   * @param request request
   * @param span span
   */
  void onRequest(Request request, Span span);

  /**
   * Decorate span on an error e.g. {@link java.net.UnknownHostException} or any exception in
   * interceptor.
   *
   * @param throwable exception
   * @param span span
   */
  void onError(Throwable throwable, Span span);

  /**
   * This is invoked after {@link okhttp3.Interceptor.Chain#proceed(Request)} in network
   * interceptor. In this method it is possible to capture server address, log redirects etc.
   *
   * @param connection connection
   * @param response response
   * @param span span
   */
  void onResponse(Connection connection, Response response, Span span);

  /**
   * Decorator which adds standard HTTP and peer tags to the span.
   *
   * <p>
   *
   * <p>On error it adds {@link Tags#ERROR} with log representing exception and on redirects adds
   * log entries with peer tags.
   */
  OkHttpClientSpanDecorator STANDARD_TAGS =
      new OkHttpClientSpanDecorator() {
        @Override
        public void onRequest(final Request request, final Span span) {
          Tags.COMPONENT.set(span, TracingCallFactory.COMPONENT_NAME);
          Tags.HTTP_METHOD.set(span, request.method());
          Tags.HTTP_URL.set(span, request.url().toString());
        }

        @Override
        public void onError(final Throwable throwable, final Span span) {
          Tags.ERROR.set(span, Boolean.TRUE);
          span.log(errorLogs(throwable));
        }

        @Override
        public void onResponse(
            final Connection connection, final Response response, final Span span) {
          Tags.HTTP_STATUS.set(span, response.code());
          Tags.PEER_HOSTNAME.set(span, connection.socket().getInetAddress().getHostName());
          Tags.PEER_PORT.set(span, connection.socket().getPort());

          if (connection.socket().getInetAddress() instanceof Inet4Address) {
            final byte[] address = connection.socket().getInetAddress().getAddress();
            Tags.PEER_HOST_IPV4.set(span, ByteBuffer.wrap(address).getInt());
          } else {
            Tags.PEER_HOST_IPV6.set(span, connection.socket().getInetAddress().toString());
          }
        }

        protected Map<String, Object> errorLogs(final Throwable throwable) {
          final Map<String, Object> errorLogs = new HashMap<>(2);
          errorLogs.put("event", Tags.ERROR.getKey());
          errorLogs.put("error.object", throwable);

          return errorLogs;
        }
      };
}
