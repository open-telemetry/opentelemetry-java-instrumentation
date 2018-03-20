package datadog.trace.instrumentation.okhttp3;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static io.opentracing.contrib.okhttp3.OkHttpClientSpanDecorator.STANDARD_TAGS;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.contrib.okhttp3.TracingInterceptor;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

@AutoService(Instrumenter.class)
public class OkHttp3Instrumentation extends Instrumenter.Configurable {

  public OkHttp3Instrumentation() {
    super("okhttp", "okhttp-3");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("okhttp3.OkHttpClient"),
            classLoaderHasClasses(
                "okhttp3.Request",
                "okhttp3.Response",
                "okhttp3.Connection",
                "okhttp3.Cookie",
                "okhttp3.ConnectionPool",
                "okhttp3.Headers"))
        .transform(
            new HelperInjector(
                "io.opentracing.contrib.okhttp3.OkHttpClientSpanDecorator",
                "io.opentracing.contrib.okhttp3.OkHttpClientSpanDecorator$1",
                "io.opentracing.contrib.okhttp3.TagWrapper",
                "io.opentracing.contrib.okhttp3.TracingInterceptor",
                "io.opentracing.contrib.okhttp3.RequestBuilderInjectAdapter",
                "io.opentracing.contrib.okhttp3.TracingCallFactory",
                "io.opentracing.contrib.okhttp3.TracingCallFactory$NetworkInterceptor",
                "io.opentracing.contrib.okhttp3.TracingCallFactory$1"))
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    isConstructor().and(takesArgument(0, named("okhttp3.OkHttpClient$Builder"))),
                    OkHttp3Advice.class.getName()))
        .asDecorator();
  }

  public static class OkHttp3Advice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addTracingInterceptor(
        @Advice.Argument(0) final OkHttpClient.Builder builder) {
      for (final Interceptor interceptor : builder.interceptors()) {
        if (interceptor instanceof TracingInterceptor) {
          return;
        }
      }
      final TracingInterceptor interceptor =
          new TracingInterceptor(GlobalTracer.get(), Collections.singletonList(STANDARD_TAGS));
      builder.addInterceptor(interceptor);
      builder.addNetworkInterceptor(interceptor);
    }
  }
}
