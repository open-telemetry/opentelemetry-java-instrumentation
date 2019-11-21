package datadog.trace.instrumentation.grizzly;

import static datadog.trace.agent.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.grizzly.GrizzlyDecorator.DECORATE;
import static datadog.trace.instrumentation.grizzly.GrizzlyRequestExtractAdapter.GETTER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.AgentSpan.Context;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.grizzly.http.server.AfterServiceListener;
import org.glassfish.grizzly.http.server.Request;

@AutoService(Instrumenter.class)
public class GrizzlyHttpHandlerInstrumentation extends Instrumenter.Default {

  public GrizzlyHttpHandlerInstrumentation() {
    super("grizzly");
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.http.server.HttpHandler");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      "datadog.trace.agent.decorator.HttpServerDecorator",
      packageName + ".GrizzlyDecorator",
      packageName + ".GrizzlyRequestExtractAdapter",
      getClass().getName() + "$SpanClosingListener"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("doHandle"))
            .and(takesArgument(0, named("org.glassfish.grizzly.http.server.Request")))
            .and(takesArgument(1, named("org.glassfish.grizzly.http.server.Response"))),
        GrizzlyHttpHandlerInstrumentation.class.getName() + "$HandleAdvice");
  }

  public static class HandleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope methodEnter(@Advice.Argument(0) final Request request) {
      if (request.getAttribute(DD_SPAN_ATTRIBUTE) != null) {
        return null;
      }

      final Context parentContext = propagate().extract(request, GETTER);
      final AgentSpan span = startSpan("grizzly.request", parentContext);
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, request);
      DECORATE.onRequest(span, request);

      final AgentScope scope = activateSpan(span, false);
      scope.setAsyncPropagation(true);

      request.setAttribute(DD_SPAN_ATTRIBUTE, span);
      request.addAfterServiceListener(SpanClosingListener.LISTENER);

      return scope;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }

      if (throwable != null) {
        final AgentSpan span = scope.span();
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
      }
      scope.close();
    }
  }

  public static class SpanClosingListener implements AfterServiceListener {
    public static final SpanClosingListener LISTENER = new SpanClosingListener();

    @Override
    public void onAfterService(final Request request) {
      final Object spanAttr = request.getAttribute(DD_SPAN_ATTRIBUTE);
      if (spanAttr instanceof AgentSpan) {
        request.removeAttribute(DD_SPAN_ATTRIBUTE);
        final AgentSpan span = (AgentSpan) spanAttr;
        DECORATE.onResponse(span, request.getResponse());
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }
  }
}
