package datadog.trace.instrumentation.grpc.client;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.grpc.ClientInterceptor;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class GrpcClientBuilderInstrumentation extends Instrumenter.Default {

  public GrpcClientBuilderInstrumentation() {
    super("grpc", "grpc-client");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return named("io.grpc.internal.AbstractManagedChannelImplBuilder");
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return classLoaderHasClasses("io.grpc.InternalServerInterceptors");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.grpc.client.GrpcInjectAdapter",
      "datadog.trace.instrumentation.grpc.client.TracingClientInterceptor",
      "datadog.trace.instrumentation.grpc.client.TracingClientInterceptor$TracingClientCall",
      "datadog.trace.instrumentation.grpc.client.TracingClientInterceptor$TracingClientCallListener",
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    return Collections.<ElementMatcher, String>singletonMap(
        isMethod().and(named("build")), AddInterceptorAdvice.class.getName());
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  public static class AddInterceptorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInterceptor(
        @Advice.FieldValue("interceptors") final List<ClientInterceptor> interceptors) {
      boolean shouldRegister = true;
      for (final ClientInterceptor interceptor : interceptors) {
        if (interceptor instanceof TracingClientInterceptor) {
          shouldRegister = false;
          break;
        }
      }
      if (shouldRegister) {
        interceptors.add(0, new TracingClientInterceptor(GlobalTracer.get()));
      }
    }
  }
}
