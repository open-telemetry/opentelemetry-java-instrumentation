package datadog.trace.instrumentation.okhttp3;

import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp interceptor to trace client requests. Interceptor adds span context into outgoing
 * requests. Please only use this instrumentation when {@link TracingCallFactory} is not possible to
 * use. This instrumentation fails to properly infer parent span when doing simultaneously
 * asynchronous calls.
 *
 * <p>
 *
 * <p>
 *
 * <p>
 *
 * <p>Initialization via {@link TracingInterceptor#addTracing(OkHttpClient.Builder, Tracer, List)}
 *
 * <p>
 *
 * <p>
 *
 * <p>
 *
 * <p>or instantiate the interceptor and add it to {@link
 * OkHttpClient.Builder#addInterceptor(Interceptor)} and {@link
 * OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
 *
 * @author Pavol Loffay
 */
public class TracingInterceptor implements Interceptor {
  private static final Logger log = Logger.getLogger(TracingInterceptor.class.getName());

  private final Tracer tracer;
  private final List<OkHttpClientSpanDecorator> decorators;

  /**
   * Create tracing interceptor. Interceptor has to be added to {@link
   * OkHttpClient.Builder#addInterceptor(Interceptor)} and {@link
   * OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
   *
   * @param tracer tracer
   */
  public TracingInterceptor(final Tracer tracer) {
    this(tracer, Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
  }

  /**
   * Create tracing interceptor. Interceptor has to be added to {@link
   * OkHttpClient.Builder#addInterceptor(Interceptor)} and {@link
   * OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
   *
   * @param tracer tracer
   * @param decorators decorators
   */
  public TracingInterceptor(final Tracer tracer, final List<OkHttpClientSpanDecorator> decorators) {
    this.tracer = tracer;
    this.decorators = new ArrayList<>(decorators);
  }

  public static OkHttpClient addTracing(final OkHttpClient.Builder builder, final Tracer tracer) {
    return TracingInterceptor.addTracing(
        builder, tracer, Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
  }

  public static OkHttpClient addTracing(
      final OkHttpClient.Builder builder,
      final Tracer tracer,
      final List<OkHttpClientSpanDecorator> decorators) {
    final TracingInterceptor tracingInterceptor = new TracingInterceptor(tracer, decorators);
    builder.interceptors().add(0, tracingInterceptor);
    builder.networkInterceptors().add(0, tracingInterceptor);
    builder.dispatcher(
        new Dispatcher(new TracedExecutorService(Executors.newFixedThreadPool(10), tracer)));
    return builder.build();
  }

  @Override
  public Response intercept(final Chain chain) throws IOException {
    Response response = null;

    // application interceptor?
    if (chain.connection() == null) {
      final Scope scope =
          tracer
              .buildSpan("http.request")
              .withTag(Tags.COMPONENT.getKey(), TracingCallFactory.COMPONENT_NAME)
              .startActive(true);

      final Request.Builder requestBuilder = chain.request().newBuilder();

      final Object tag = chain.request().tag();
      final TagWrapper tagWrapper =
          tag instanceof TagWrapper ? (TagWrapper) tag : new TagWrapper(tag);
      requestBuilder.tag(new TagWrapper(tagWrapper, scope.span()));

      try {
        response = chain.proceed(requestBuilder.build());
      } catch (final Throwable ex) {
        for (final OkHttpClientSpanDecorator spanDecorator : decorators) {
          spanDecorator.onError(ex, scope.span());
        }
        throw ex;
      } finally {
        scope.close();
      }
    } else {
      final Object tag = chain.request().tag();
      if (tag instanceof TagWrapper) {
        final TagWrapper tagWrapper = (TagWrapper) tag;
        response =
            new TracingCallFactory.NetworkInterceptor(
                    tracer, tagWrapper.getSpan().context(), decorators)
                .intercept(chain);
      } else {
        log.severe("tag is null or not an instance of TagWrapper, skipping decorator onResponse()");
      }
    }

    return response;
  }
}
