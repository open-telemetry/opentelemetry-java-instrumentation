package datadog.trace.instrumentation.springweb;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClassWithField;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import org.springframework.web.servlet.HandlerMapping;

@AutoService(Instrumenter.class)
public final class SpringWebInstrumentation extends Instrumenter.Configurable {

  public SpringWebInstrumentation() {
    super("spring-web");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            not(isInterface())
                .and(hasSuperType(named("org.springframework.web.servlet.HandlerAdapter"))),
            classLoaderHasClassWithField(
                "org.springframework.web.servlet.HandlerMapping",
                "BEST_MATCHING_PATTERN_ATTRIBUTE"))
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(isPublic())
                        .and(nameStartsWith("handle"))
                        .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest"))),
                    SpringWebAdvice.class.getName()))
        .asDecorator();
  }

  public static class SpringWebAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameResource(@Advice.Argument(0) final HttpServletRequest request) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope != null && request != null) {
        final String method = request.getMethod();
        final Object bestMatchingPattern =
            request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (method != null && bestMatchingPattern != null) {
          final String resourceName = method + " " + bestMatchingPattern;
          scope.span().setTag(DDTags.RESOURCE_NAME, resourceName);
          scope.span().setTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET);
        }
      }
    }
  }
}
