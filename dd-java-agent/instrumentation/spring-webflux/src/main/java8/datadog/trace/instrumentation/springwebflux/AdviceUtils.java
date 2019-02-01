package datadog.trace.instrumentation.springwebflux;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

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
    // Span could have been removed and finished by other thread before we got here
    finishSpanIfPresent((Span) exchange.getAttributes().remove(SPAN_ATTRIBUTE), throwable);
  }

  public static void finishSpanIfPresent(
    final ServerRequest serverRequest, final Throwable throwable) {
    // Span could have been removed and finished by other thread before we got here
    finishSpanIfPresent((Span) serverRequest.attributes().remove(SPAN_ATTRIBUTE), throwable);
  }

  private static void finishSpanIfPresent(final Span span, final Throwable throwable) {
    if (span != null) {
      if (throwable != null) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      span.finish();
    }
  }
}
