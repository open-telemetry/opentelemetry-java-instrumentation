package datadog.trace.instrumentation.springwebflux;

import datadog.trace.instrumentation.reactor.core.ReactorCoreAdviceUtils;
import io.opentracing.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
public class AdviceUtils {

  public static final String SPAN_ATTRIBUTE = "datadog.trace.instrumentation.springwebflux.Span";
  public static final String PARENT_SPAN_ATTRIBUTE =
    "datadog.trace.instrumentation.springwebflux.ParentSpan";

  public static String parseOperationName(final Object handler) {
    final String className = parseClassName(handler.getClass());
    final String operationName;
    final int lambdaIdx = className.indexOf("$$Lambda$");

    if (lambdaIdx > -1) {
      operationName = className.substring(0, lambdaIdx) + ".lambda";
    } else {
      operationName = className + ".handle";
    }
    return operationName;
  }

  public static String parseClassName(final Class clazz) {
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
    return className;
  }

  public static void finishSpanIfPresent(
    final ServerWebExchange exchange, final Throwable throwable) {
    ReactorCoreAdviceUtils.finishSpanIfPresent(
      (Span) exchange.getAttributes().remove(SPAN_ATTRIBUTE), throwable);
  }

  public static void finishSpanIfPresent(
    final ServerRequest serverRequest, final Throwable throwable) {
    ReactorCoreAdviceUtils.finishSpanIfPresent(
      (Span) serverRequest.attributes().remove(SPAN_ATTRIBUTE), throwable);
  }
}
