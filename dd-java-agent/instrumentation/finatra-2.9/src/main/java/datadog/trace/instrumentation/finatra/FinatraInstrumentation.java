package datadog.trace.instrumentation.finatra;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.finatra.FinatraDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import java.lang.reflect.Method;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Some;

@AutoService(Instrumenter.class)
public class FinatraInstrumentation extends Instrumenter.Default {
  public FinatraInstrumentation() {
    super("finatra");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      "datadog.trace.agent.decorator.HttpServerDecorator",
      packageName + ".FinatraDecorator",
      FinatraInstrumentation.class.getName() + "$Listener"
    };
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("com.twitter.finatra.http.internal.routing.Route")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("handleMatch"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("com.twitter.finagle.http.Request"))),
        FinatraInstrumentation.class.getName() + "$RouteAdvice");
  }

  public static class RouteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope nameSpan(
        @Advice.Argument(0) final Request request,
        @Advice.FieldValue("path") final String path,
        @Advice.FieldValue("clazz") final Class clazz,
        @Advice.Origin final Method method) {

      // Update the parent "netty.request"
      final AgentSpan parent = activeSpan();
      parent.setTag(DDTags.RESOURCE_NAME, request.method().name() + " " + path);
      parent.setTag(Tags.COMPONENT, "finatra");
      parent.setSpanName("finatra.request");

      final AgentSpan span = startSpan("finatra.controller");
      DECORATE.afterStart(span);
      span.setTag(DDTags.RESOURCE_NAME, DECORATE.spanNameForClass(clazz));

      final AgentScope scope = activateSpan(span, false);
      scope.setAsyncPropagation(true);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void setupCallback(
        @Advice.Enter final AgentScope scope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return final Some<Future<Response>> responseOption) {

      if (scope == null) {
        return;
      }

      final AgentSpan span = scope.span();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
        scope.close();
        return;
      }

      responseOption.get().addEventListener(new Listener(scope));
    }
  }

  public static class Listener implements FutureEventListener<Response> {
    private final AgentScope scope;

    public Listener(final AgentScope scope) {
      this.scope = scope;
    }

    @Override
    public void onSuccess(final Response response) {
      // Don't use DECORATE.onResponse because this is the controller span
      if (Config.get().getHttpServerErrorStatuses().contains(DECORATE.status(response))) {
        scope.span().setError(true);
      }

      DECORATE.beforeFinish(scope.span());
      scope.span().finish();
      scope.close();
    }

    @Override
    public void onFailure(final Throwable cause) {
      DECORATE.onError(scope.span(), cause);
      DECORATE.beforeFinish(scope.span());
      scope.span().finish();
      scope.close();
    }
  }
}
