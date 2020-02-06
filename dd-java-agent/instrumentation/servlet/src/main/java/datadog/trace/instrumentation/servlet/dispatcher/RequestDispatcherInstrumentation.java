package datadog.trace.instrumentation.servlet.dispatcher;

import static datadog.trace.agent.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.servlet.ServletRequestSetter.SETTER;
import static datadog.trace.instrumentation.servlet.dispatcher.RequestDispatcherDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RequestDispatcherInstrumentation extends Instrumenter.Default {
  public RequestDispatcherInstrumentation() {
    super("servlet", "servlet-dispatcher");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.servlet.ServletRequestSetter",
      "datadog.trace.agent.decorator.BaseDecorator",
      packageName + ".RequestDispatcherDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.servlet.RequestDispatcher")));
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("javax.servlet.RequestDispatcher", String.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("forward")
            .or(named("include"))
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        RequestDispatcherAdvice.class.getName());
  }

  public static class RequestDispatcherAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope start(
        @Advice.Origin("#m") final String method,
        @Advice.This final RequestDispatcher dispatcher,
        @Advice.Local("_requestSpan") Object requestSpan,
        @Advice.Argument(0) final ServletRequest request) {
      if (activeSpan() == null) {
        // Don't want to generate a new top-level span
        return null;
      }

      final AgentSpan span = startSpan("servlet." + method);
      DECORATE.afterStart(span);

      final String target =
          InstrumentationContext.get(RequestDispatcher.class, String.class).get(dispatcher);
      span.setTag(DDTags.RESOURCE_NAME, target);

      // In case we lose context, inject trace into to the request.
      propagate().inject(span, request, SETTER);

      // temporarily replace from request to avoid spring resource name bubbling up:
      requestSpan = request.getAttribute(DD_SPAN_ATTRIBUTE);
      request.setAttribute(DD_SPAN_ATTRIBUTE, span);

      return activateSpan(span, true).setAsyncPropagation(true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stop(
        @Advice.Enter final AgentScope scope,
        @Advice.Local("_requestSpan") final Object requestSpan,
        @Advice.Argument(0) final ServletRequest request,
        @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }

      if (requestSpan != null) {
        // now add it back...
        request.setAttribute(DD_SPAN_ATTRIBUTE, requestSpan);
      }

      DECORATE.onError(scope, throwable);
      DECORATE.beforeFinish(scope);
      scope.close();
    }
  }
}
