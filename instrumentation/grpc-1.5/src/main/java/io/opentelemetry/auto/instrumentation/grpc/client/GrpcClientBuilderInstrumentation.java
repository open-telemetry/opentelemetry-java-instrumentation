package io.opentelemetry.auto.instrumentation.grpc.client;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.grpc.ClientInterceptor;
import io.opentelemetry.auto.tooling.Instrumenter;
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
      "io.opentelemetry.auto.instrumentation.grpc.client.GrpcInjectAdapter",
      "io.opentelemetry.auto.instrumentation.grpc.client.TracingClientInterceptor",
      "io.opentelemetry.auto.instrumentation.grpc.client.TracingClientInterceptor$TracingClientCall",
      "io.opentelemetry.auto.instrumentation.grpc.client.TracingClientInterceptor$TracingClientCallListener",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      packageName + ".GrpcClientDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("build")),
        GrpcClientBuilderInstrumentation.class.getName() + "$AddInterceptorAdvice");
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
        interceptors.add(0, TracingClientInterceptor.INSTANCE);
      }
    }
  }
}
