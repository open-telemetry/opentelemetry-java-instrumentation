package datadog.trace.instrumentation.grpc.server;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.grpc.ServerInterceptor;
import io.opentracing.util.GlobalTracer;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class GrpcServerBuilderInstrumentation extends Instrumenter.Default {

  public GrpcServerBuilderInstrumentation() {
    super("grpc", "grpc-server");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.grpc.internal.AbstractServerImplBuilder");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.grpc.server.TracingServerInterceptor",
      "datadog.trace.instrumentation.grpc.server.TracingServerInterceptor$TracingServerCallListener",
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      packageName + ".GrpcServerDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(isMethod().and(named("build")), AddInterceptorAdvice.class.getName());
  }

  public static class AddInterceptorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInterceptor(
        @Advice.FieldValue("interceptors") final List<ServerInterceptor> interceptors) {
      boolean shouldRegister = true;
      for (final ServerInterceptor interceptor : interceptors) {
        if (interceptor instanceof TracingServerInterceptor) {
          shouldRegister = false;
          break;
        }
      }
      if (shouldRegister) {
        interceptors.add(0, new TracingServerInterceptor(GlobalTracer.get()));
      }
    }
  }
}
