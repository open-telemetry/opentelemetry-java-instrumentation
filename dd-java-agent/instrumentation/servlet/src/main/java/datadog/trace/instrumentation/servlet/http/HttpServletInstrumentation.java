package datadog.trace.instrumentation.servlet.http;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.servlet.http.HttpServletDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.lang.reflect.Method;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class HttpServletInstrumentation extends Instrumenter.Default {
  public HttpServletInstrumentation() {
    super("servlet-service");
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator", packageName + ".HttpServletDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.servlet.http.HttpServlet")));
  }

  /**
   * Here we are instrumenting the protected method for HttpServlet. This should ensure that this
   * advice is always called after Servlet3Instrumentation which is instrumenting the public method.
   */
  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("service")
            .or(nameStartsWith("do")) // doGet, doPost, etc
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(isProtected().or(isPublic())),
        HttpServletAdvice.class.getName());
  }

  public static class HttpServletAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope start(@Advice.Origin final Method method) {

      if (activeSpan() == null) {
        // Don't want to generate a new top-level span
        return null;
      }

      final AgentSpan span = startSpan("servlet." + method.getName());
      DECORATE.afterStart(span);

      // Here we use the Method instead of "this.class.name" to distinguish calls to "super".
      span.setTag(DDTags.RESOURCE_NAME, DECORATE.spanNameForMethod(method));

      return activateSpan(span, true).setAsyncPropagation(true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }
      DECORATE.onError(scope, throwable);
      DECORATE.beforeFinish(scope);
      scope.close();
    }
  }
}
