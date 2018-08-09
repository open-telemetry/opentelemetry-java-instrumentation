package datadog.trace.instrumentation.springweb;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClassWithField;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.Controller;

@AutoService(Instrumenter.class)
public final class HandlerAdapterInstrumentation extends Instrumenter.Default {

  public HandlerAdapterInstrumentation() {
    super("spring-web");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("org.springframework.web.servlet.HandlerAdapter")));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return classLoaderHasClassWithField(
        "org.springframework.web.servlet.HandlerMapping", "BEST_MATCHING_PATTERN_ATTRIBUTE");
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    return Collections.<ElementMatcher, String>singletonMap(
        isMethod()
            .and(isPublic())
            .and(nameStartsWith("handle"))
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArguments(3)),
        ControllerAdvice.class.getName());
  }

  public static class ControllerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope nameResourceAndStartSpan(
        @Advice.Argument(0) final HttpServletRequest request,
        @Advice.Argument(2) final Object handler) {
      // Name the parent span based on the matching pattern
      // This is likely the servlet.request span.
      final Scope parentScope = GlobalTracer.get().scopeManager().active();
      if (parentScope != null && request != null) {
        final String method = request.getMethod();
        final Object bestMatchingPattern =
            request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (method != null && bestMatchingPattern != null) {
          final String resourceName = method + " " + bestMatchingPattern;
          parentScope.span().setTag(DDTags.RESOURCE_NAME, resourceName);
          parentScope.span().setTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET);
        }
      }

      // Now create a span for controller execution.

      final Class<?> clazz;
      final String methodName;

      if (handler instanceof HandlerMethod) {
        // name span based on the class and method name defined in the handler
        final Method method = ((HandlerMethod) handler).getMethod();
        clazz = method.getDeclaringClass();
        methodName = method.getName();
      } else if (handler instanceof HttpRequestHandler) {
        // org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter
        clazz = handler.getClass();
        methodName = "handleRequest";
      } else if (handler instanceof Controller) {
        // org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter
        clazz = handler.getClass();
        methodName = "handleRequest";
      } else if (handler instanceof Servlet) {
        // org.springframework.web.servlet.handler.SimpleServletHandlerAdapter
        clazz = handler.getClass();
        methodName = "service";
      } else {
        // perhaps org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter
        clazz = handler.getClass();
        methodName = "<annotation>";
      }

      String className = clazz.getSimpleName();
      if (className.isEmpty()) {
        className = clazz.getName();
        if (clazz.getPackage() != null) {
          final String pkgName = clazz.getPackage().getName();
          if (!pkgName.isEmpty()) {
            className = clazz.getName().replace(pkgName, "").substring(1);
          }
        }
      }

      final String operationName = className + "." + methodName;

      return GlobalTracer.get()
          .buildSpan(operationName)
          .withTag(Tags.COMPONENT.getKey(), "spring-web-controller")
          .startActive(true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      scope.close();
    }
  }
}
