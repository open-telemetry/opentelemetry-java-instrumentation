package datadog.trace.instrumentation.springwebflux;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.asm.Advice;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.server.HandlerFunction;

public class HandlerFunctionAdapterAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void recordHandlerFunctionTag(
      @Advice.Thrown final Throwable throwable, @Advice.Argument(1) final Object handler) {

    final Span activeSpan = GlobalTracer.get().activeSpan();

    if (activeSpan != null && handler != null) {
      final Class clazz = handler.getClass();

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
      LoggerFactory.getLogger(HandlerFunction.class).warn(className);
      final String operationName;
      final int lambdaIdx = className.indexOf("$$Lambda$");

      if (lambdaIdx > -1) {
        operationName = className.substring(0, lambdaIdx) + ".lambda";
      } else {
        operationName = className + ".handle";
      }
      activeSpan.setOperationName(operationName);
      activeSpan.setTag("handler.type", clazz.getName());

      if (throwable != null) {
        Tags.ERROR.set(activeSpan, true);
        activeSpan.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
    }
  }
}
