package datadog.trace.instrumentation.okhttp3;

import static datadog.trace.instrumentation.okhttp3.OkHttpClientSpanDecorator.STANDARD_TAGS;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

@AutoService(Instrumenter.class)
public class OkHttp3Instrumentation extends Instrumenter.Default {

  public OkHttp3Instrumentation() {
    super("okhttp", "okhttp-3");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("okhttp3.OkHttpClient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.okhttp3.OkHttpClientSpanDecorator",
      "datadog.trace.instrumentation.okhttp3.OkHttpClientSpanDecorator$1",
      "datadog.trace.instrumentation.okhttp3.RequestBuilderInjectAdapter",
      "datadog.trace.instrumentation.okhttp3.TagWrapper",
      "datadog.trace.instrumentation.okhttp3.TracedCallable",
      "datadog.trace.instrumentation.okhttp3.TracedExecutor",
      "datadog.trace.instrumentation.okhttp3.TracedExecutorService",
      "datadog.trace.instrumentation.okhttp3.TracedRunnable",
      "datadog.trace.instrumentation.okhttp3.TracingInterceptor",
      "datadog.trace.instrumentation.okhttp3.TracingCallFactory",
      "datadog.trace.instrumentation.okhttp3.TracingCallFactory$NetworkInterceptor",
      "datadog.trace.instrumentation.okhttp3.TracingCallFactory$1"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isConstructor().and(takesArgument(0, named("okhttp3.OkHttpClient$Builder"))),
        OkHttp3Advice.class.getName());
    return transformers;
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
