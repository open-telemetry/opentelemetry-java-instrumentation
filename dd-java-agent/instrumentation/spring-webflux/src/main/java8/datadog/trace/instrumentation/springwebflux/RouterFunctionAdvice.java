package datadog.trace.instrumentation.springwebflux;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.asm.Advice;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.ServerRequest;

public class RouterFunctionAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void nameResource(
      @Advice.FieldValue(value = "predicate") final RequestPredicate predicate,
      @Advice.Argument(0) final ServerRequest serverRequest) {

    if (predicate == null) {
      return;
    }

    final Class predicateEnclosingClass = predicate.getClass().getEnclosingClass();
    final String predicateString = predicate.toString();
    if (predicate.test(serverRequest)
        && serverRequest != null
        && predicateString != null
        && !predicateString.isEmpty()
        && predicateEnclosingClass == RequestPredicates.class) {

      // only change parent span if the predicate is one of those enclosed in
      // org.springframework.web.reactive.function.server RequestPredicates
      // otherwise the parent may have weird resource names such as lambda request predicate class
      // names that arise from webflux error handling

      final String resourceName =
          predicateString.replaceAll("[\\(\\)&|]", "").replaceAll("[ \\t]+", " ");

      // to be used as resource name by netty span, most likely
      DispatcherHandlerMonoBiConsumer.setTLPathUrl(resourceName);

      // should be the dispatcher handler span
      final Scope activeScope = GlobalTracer.get().scopeManager().active();
      if (activeScope != null) {
        activeScope.span().setTag("request.predicate", predicateString);
        activeScope.span().setTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_SERVER);
      }
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void recordThrowable(@Advice.Thrown final Throwable throwable) {
    final Scope scope = GlobalTracer.get().scopeManager().active();
    if (scope != null && throwable != null) {
      final Span span = scope.span();
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
    }
  }
}
