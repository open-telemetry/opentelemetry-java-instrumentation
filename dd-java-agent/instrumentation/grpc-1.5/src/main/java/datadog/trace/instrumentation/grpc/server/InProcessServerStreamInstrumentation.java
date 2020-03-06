package datadog.trace.instrumentation.grpc.server;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeScope;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * The InProcessTransport calls the client response in process, so we have to disable async
 * propagation to allow spans to complete and be reported properly.
 */
@AutoService(Instrumenter.class)
public class InProcessServerStreamInstrumentation extends Instrumenter.Default {

  public InProcessServerStreamInstrumentation() {
    super("grpc", "grpc-server");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.grpc.inprocess.InProcessTransport$InProcessStream$InProcessServerStream");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(isPublic(), getClass().getName() + "$DisableAsyncPropagationAdvice");
  }

  public static class DisableAsyncPropagationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static TraceScope enter() {
      final TraceScope scope = activeScope();
      if (scope != null && scope.isAsyncPropagating()) {
        scope.setAsyncPropagation(false);
        return scope;
      }
      return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void exit(@Advice.Enter final TraceScope scopeToReenable) {
      if (scopeToReenable != null) {
        scopeToReenable.setAsyncPropagation(true);
      }
    }
  }
}
