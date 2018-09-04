package datadog.trace.instrumentation.springwebflux;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.Collections;
import net.bytebuddy.asm.Advice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

public class RequestMappingInfoHandlerMappingAdvice {

  @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
  public static void nameSpan(
      @Advice.Argument(1) final HandlerMethod handlerMethod,
      @Advice.Argument(2) final ServerWebExchange serverWebExchange,
      @Advice.Thrown final Throwable throwable) {

    final Scope scope = GlobalTracer.get().scopeManager().active();
    if (scope != null) {
      final Span span = scope.span();
      final PathPattern bestPattern =
          serverWebExchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

      if (bestPattern != null) {
        DispatcherHandlerMonoBiConsumer.setTLPathUrl(
            serverWebExchange.getRequest().getMethodValue() + " " + bestPattern.getPatternString());
      }

      if (handlerMethod != null) {
        final Method method;
        final Class clazz;
        final String methodName;
        clazz = handlerMethod.getMethod().getDeclaringClass();
        method = handlerMethod.getMethod();
        methodName = method.getName();
        span.setTag("handler.type", clazz.getName());

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
        span.setOperationName(className + "." + methodName);
      }

      if (throwable != null) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
    }
  }
}
