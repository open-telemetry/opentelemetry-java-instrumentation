package datadog.trace.instrumentation.grpc.server;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.grpc.ServerInterceptor;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class GrpcServerBuilderInstrumentation extends Instrumenter.Default {

  public GrpcServerBuilderInstrumentation() {
    super("grpc", "grpc-server");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return named("io.grpc.internal.AbstractServerImplBuilder");
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return classLoaderHasClasses("io.grpc.InternalServerInterceptors");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.grpc.server.TracingServerInterceptor",
      "datadog.trace.instrumentation.grpc.server.TracingServerInterceptor$TracingServerCallListener",
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
