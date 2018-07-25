package datadog.trace.agent.test

import io.opentracing.Scope
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
      Scope scope =
        GlobalTracer.get()
          .buildSpan("test-http-server")
          .asChildOf(extractedContext)
          .startActive(true)
      scope.close()
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
