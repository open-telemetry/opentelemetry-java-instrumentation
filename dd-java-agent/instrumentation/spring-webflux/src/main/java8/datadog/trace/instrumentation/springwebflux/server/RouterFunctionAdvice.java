package datadog.trace.instrumentation.springwebflux.server;

import net.bytebuddy.asm.Advice;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

/**
 * This advice is responsible for setting additional span parameters for routes implemented with
 * functional interface.
 */
public class RouterFunctionAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.This final RouterFunction thiz,
      @Advice.Argument(0) final ServerRequest serverRequest,
      @Advice.Return(readOnly = false) Mono<HandlerFunction<?>> result,
      @Advice.Thrown final Throwable throwable) {
    if (throwable == null) {
      result = result.doOnSuccessOrError(new RouteOnSuccessOrError(thiz, serverRequest));
    } else {
      AdviceUtils.finishSpanIfPresent(serverRequest, throwable);
    }
  }
}
