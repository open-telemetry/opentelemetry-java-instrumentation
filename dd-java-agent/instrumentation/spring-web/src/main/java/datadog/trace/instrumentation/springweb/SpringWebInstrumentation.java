package datadog.trace.instrumentation.springweb;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClassWithField;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.servlet.HandlerMapping;

@AutoService(Instrumenter.class)
public final class SpringWebInstrumentation extends Instrumenter.Default {

  public SpringWebInstrumentation() {
    super("spring-web");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return not(isInterface())
        .and(failSafe(hasSuperType(named("org.springframework.web.servlet.HandlerAdapter"))));
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return classLoaderHasClassWithField(
        "org.springframework.web.servlet.HandlerMapping", "BEST_MATCHING_PATTERN_ATTRIBUTE");
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(nameStartsWith("handle"))
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest"))),
        SpringWebNamingAdvice.class.getName());
    return transformers;
  }

  @AutoService(Instrumenter.class)
  public static class DispatcherServletInstrumentation extends Default {

    public DispatcherServletInstrumentation() {
      super("spring-web");
    }

    @Override
    public ElementMatcher typeMatcher() {
      return not(isInterface()).and(named("org.springframework.web.servlet.DispatcherServlet"));
    }

    @Override
    public Map<ElementMatcher, String> transformers() {
      Map<ElementMatcher, String> transformers = new HashMap<>();
      transformers.put(
          isMethod()
              .and(isProtected())
              .and(nameStartsWith("processHandlerException"))
              .and(takesArgument(3, Exception.class)),
          SpringWebErrorHandlerAdvice.class.getName());
      return transformers;
    }
  }

  public static class SpringWebNamingAdvice {

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

  public static class SpringWebErrorHandlerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameResource(@Advice.Argument(3) final Exception exception) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope != null && exception != null) {
        final Span span = scope.span();
        span.log(Collections.singletonMap(ERROR_OBJECT, exception));
        // We want to capture the stacktrace, but that doesn't mean it should be an error.
        // We rely on a decorator to set the error state based on response code. (5xx -> error)
        Tags.ERROR.set(span, false);
      }
    }
  }
}
