package datadog.trace.instrumentation.okhttp3;

import static datadog.trace.instrumentation.okhttp3.OkHttpClientDecorator.DECORATE;

import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(final Chain chain) throws IOException {
    if (chain.request().header("Datadog-Meta-Lang") != null) {
      return chain.proceed(chain.request());
    }

    Response response = null;

    // application interceptor?
    if (chain.connection() == null) {
      final Scope scope = GlobalTracer.get().buildSpan("okhttp.http").startActive(true);
      DECORATE.afterStart(scope);

      final Request.Builder requestBuilder = chain.request().newBuilder();

      final Object tag = chain.request().tag();
      final TagWrapper tagWrapper =
          tag instanceof TagWrapper ? (TagWrapper) tag : new TagWrapper(tag);
      requestBuilder.tag(new TagWrapper(tagWrapper, scope.span()));

      try {
        response = chain.proceed(requestBuilder.build());
      } catch (final Throwable ex) {
        DECORATE.onError(scope, ex);
        throw ex;
      } finally {
        DECORATE.beforeFinish(scope);
        scope.close();
      }
    } else {
      final Object tag = chain.request().tag();
      if (tag instanceof TagWrapper) {
        final TagWrapper tagWrapper = (TagWrapper) tag;
        response =
            new TracingCallFactory.NetworkInterceptor(tagWrapper.getSpan().context())
                .intercept(chain);
      } else {
        log.error("tag is null or not an instance of TagWrapper, skipping decorator onResponse()");
      }
    }

    return response;
  }
}
