package datadog.trace.agent.test

import io.opentracing.SpanContext
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMap
import io.opentracing.util.GlobalTracer
import ratpack.handling.Context

class RatpackUtils {

  static handleDistributedRequest(Context context) {
    boolean isDDServer = true
    if (context.request.getHeaders().contains("is-dd-server")) {
      isDDServer = Boolean.parseBoolean(context.request.getHeaders().get("is-dd-server"))
    }
    if (isDDServer) {
      final SpanContext extractedContext =
        GlobalTracer.get()
          .extract(Format.Builtin.HTTP_HEADERS, new RatpackResponseAdapter(context))
      def builder = GlobalTracer.get()
        .buildSpan("test-http-server")
      if (extractedContext != null) {
        builder.asChildOf(extractedContext)
      }
      builder.start().finish()
    }
  }

  private static class RatpackResponseAdapter implements TextMap {
    final Context context

    RatpackResponseAdapter(Context context) {
      this.context = context
    }

    @Override
    void put(String key, String value) {
      context.response.set(key, value)
    }

    @Override
    Iterator<Map.Entry<String, String>> iterator() {
      return context.request.getHeaders().asMultiValueMap().entrySet().iterator()
    }
  }
}
