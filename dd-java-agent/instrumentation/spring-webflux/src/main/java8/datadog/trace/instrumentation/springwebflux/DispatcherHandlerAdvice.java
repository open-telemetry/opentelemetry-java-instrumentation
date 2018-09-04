package datadog.trace.instrumentation.springwebflux;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.opentracing.Scope;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.asm.Advice;
import reactor.core.publisher.Mono;

public class DispatcherHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan() {
    return GlobalTracer.get()
        .buildSpan("DispatcherHandler.handle")
        .withTag(Tags.COMPONENT.getKey(), "spring-webflux-controller")
        .startActive(true);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void addBehaviorTrigger(
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return(readOnly = false) Mono<Void> returnMono) {

    if (scope != null) {
      if (throwable != null) {
        Tags.ERROR.set(scope.span(), true);
        scope.span().log(Collections.singletonMap(ERROR_OBJECT, throwable));
      } else {
        returnMono = returnMono.doOnSuccessOrError(new DispatcherHandlerMonoBiConsumer<>(scope));
      }
    }
  }
}
