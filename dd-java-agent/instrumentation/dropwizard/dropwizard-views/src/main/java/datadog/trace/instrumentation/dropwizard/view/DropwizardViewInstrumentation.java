package datadog.trace.instrumentation.dropwizard.view;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.Tags;
import io.dropwizard.views.View;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class DropwizardViewInstrumentation extends Instrumenter.Default {

  public DropwizardViewInstrumentation() {
    super("dropwizard", "dropwizard-view");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("io.dropwizard.views.ViewRenderer")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("render"))
            .and(takesArgument(0, named("io.dropwizard.views.View")))
            .and(isPublic()),
        RenderAdvice.class.getName());
  }

  public static class RenderAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(
        @Advice.This final Object obj, @Advice.Argument(0) final View view) {
      if (activeSpan() == null) {
        return null;
      }
      final AgentSpan span =
          startSpan("view.render")
              .setTag(DDTags.RESOURCE_NAME, "View " + view.getTemplateName())
              .setTag(Tags.COMPONENT, "dropwizard-view")
              .setTag("span.origin.type", obj.getClass().getSimpleName());
      return activateSpan(span, true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }
      final AgentSpan span = scope.span();
      if (throwable != null) {
        span.setError(true);
        span.addThrowable(throwable);
      }
      scope.close();
    }
  }
}
