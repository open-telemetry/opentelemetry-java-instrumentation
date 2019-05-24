package datadog.trace.instrumentation.springweb;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.springweb.SpringWebHttpServerDecorator.DECORATE;
import static java.util.Collections.singletonMap;
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
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.Map;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.method.HandlerMethod;
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
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      "datadog.trace.agent.decorator.HttpServerDecorator",
      packageName + ".SpringWebHttpServerDecorator",
      packageName + ".SpringWebHttpServerDecorator$1",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
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
      if (parentScope != null) {
        DECORATE.onRequest(parentScope.span(), request);
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

      final String operationName = DECORATE.spanNameForClass(clazz) + "." + methodName;

      final Scope scope = GlobalTracer.get().buildSpan(operationName).startActive(true);
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }
      DECORATE.afterStart(scope);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      DECORATE.onError(scope, throwable);
      DECORATE.beforeFinish(scope);
      scope.close();
    }
  }
}
