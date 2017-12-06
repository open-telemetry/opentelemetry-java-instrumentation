package dd.inst.okhttp3;

import static dd.trace.ClassLoaderMatcher.classLoaderHasClasses;
import static dd.trace.ExceptionHandlers.defaultExceptionHandler;
import static io.opentracing.contrib.okhttp3.OkHttpClientSpanDecorator.STANDARD_TAGS;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import dd.trace.Instrumenter;
import io.opentracing.contrib.okhttp3.TracingInterceptor;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import okhttp3.OkHttpClient;

@AutoService(Instrumenter.class)
public class OkHttp3Instrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("okhttp3.OkHttpClient"),
            classLoaderHasClasses("okhttp3.Cookie", "okhttp3.ConnectionPool", "okhttp3.Headers"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    isConstructor().and(takesArgument(0, named("okhttp3.OkHttpClient$Builder"))),
                    OkHttp3Advice.class.getName())
                .withExceptionHandler(defaultExceptionHandler()))
        .asDecorator();
  }

  public static class OkHttp3Advice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addTracingInterceptor(
        @Advice.Argument(0) final OkHttpClient.Builder builder) {
      final TracingInterceptor interceptor =
          new TracingInterceptor(GlobalTracer.get(), Collections.singletonList(STANDARD_TAGS));
      builder.addInterceptor(interceptor);
      builder.addNetworkInterceptor(interceptor);
    }
  }
}
