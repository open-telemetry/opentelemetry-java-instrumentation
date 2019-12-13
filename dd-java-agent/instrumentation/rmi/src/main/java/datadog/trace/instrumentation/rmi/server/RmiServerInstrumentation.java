package datadog.trace.instrumentation.rmi.server;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.rmi.server.ServerDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.AgentTracer;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RmiServerInstrumentation extends Instrumenter.Default {

  public RmiServerInstrumentation() {
    super("rmi", "rmi-server");
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(Thread.class.getName(), AgentSpan.Context.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ServerDecorator", "datadog.trace.agent.decorator.BaseDecorator"
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("java.rmi.server.RemoteServer")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(not(isStatic())),
        "datadog.trace.instrumentation.rmi.server.RmiServerInstrumentation$ServerAdvice");
  }

  public static class ServerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = true)
    public static AgentScope onEnter(
        @Advice.This final Object thiz, @Advice.Origin(value = "#m") final String method) {
      final ContextStore<Thread, AgentSpan.Context> callableContextStore =
          InstrumentationContext.get(Thread.class, AgentSpan.Context.class);

      final AgentSpan span =
          startSpan(callableContextStore)
              .setTag(DDTags.RESOURCE_NAME, thiz.getClass().getSimpleName() + "#" + method)
              .setTag("span.origin.type", thiz.getClass().getCanonicalName());
      DECORATE.afterStart(span);
      return activateSpan(span, true);
    }

    public static AgentSpan startSpan(
        final ContextStore<Thread, AgentSpan.Context> callableContextStore) {
      if (activeSpan() != null) {
        return AgentTracer.startSpan("rmi.request");
      }

      final AgentSpan.Context context = callableContextStore.get(Thread.currentThread());

      if (context == null) {
        return AgentTracer.startSpan("rmi.request");
      } else {
        return AgentTracer.startSpan("rmi.request", context);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }

      DECORATE.onError(scope, throwable);

      scope.close();
    }
  }
}
