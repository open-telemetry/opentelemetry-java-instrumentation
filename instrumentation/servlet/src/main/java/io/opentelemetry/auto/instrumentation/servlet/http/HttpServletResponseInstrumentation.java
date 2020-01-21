package io.opentelemetry.auto.instrumentation.servlet.http;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.propagate;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.servlet.ServletRequestSetter;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class HttpServletResponseInstrumentation extends Instrumenter.Default {
  public HttpServletResponseInstrumentation() {
    super("servlet", "servlet-response");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.instrumentation.servlet.ServletRequestSetter",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      packageName + ".HttpServletResponseDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("javax.servlet.http.HttpServletResponse")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(named("sendError").or(named("sendRedirect")), SendAdvice.class.getName());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(
        "javax.servlet.http.HttpServletResponse", "javax.servlet.http.HttpServletRequest");
  }

  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope start(
        @Advice.Origin("#m") final String method, @Advice.This final HttpServletResponse resp) {
      if (activeSpan() == null) {
        // Don't want to generate a new top-level span
        return null;
      }

      final HttpServletRequest req =
          InstrumentationContext.get(HttpServletResponse.class, HttpServletRequest.class).get(resp);
      if (req == null) {
        // Missing the response->request linking... probably in a wrapped instance.
        return null;
      }

      final AgentSpan span = startSpan("servlet.response");
      HttpServletResponseDecorator.DECORATE.afterStart(span);

      span.setAttribute(MoreTags.RESOURCE_NAME, "HttpServletResponse." + method);

      // In case we lose context, inject trace into to the request.
      propagate().inject(span, req, ServletRequestSetter.SETTER);

      return activateSpan(span, true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }
      HttpServletResponseDecorator.DECORATE.onError(scope, throwable);
      HttpServletResponseDecorator.DECORATE.beforeFinish(scope);
      scope.close();
    }
  }
}
