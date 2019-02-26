package datadog.trace.instrumentation.grpc.client;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.grpc.ClientInterceptor;
import io.opentracing.util.GlobalTracer;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class GrpcClientBuilderInstrumentation extends Instrumenter.Default {

  public GrpcClientBuilderInstrumentation() {
    super("grpc", "grpc-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.grpc.internal.AbstractManagedChannelImplBuilder");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.grpc.client.GrpcInjectAdapter",
      "datadog.trace.instrumentation.grpc.client.TracingClientInterceptor",
      "datadog.trace.instrumentation.grpc.client.TracingClientInterceptor$TracingClientCall",
      "datadog.trace.instrumentation.grpc.client.TracingClientInterceptor$TracingClientCallListener",
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      packageName + ".GrpcClientDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(isMethod().and(named("build")), AddInterceptorAdvice.class.getName());
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
