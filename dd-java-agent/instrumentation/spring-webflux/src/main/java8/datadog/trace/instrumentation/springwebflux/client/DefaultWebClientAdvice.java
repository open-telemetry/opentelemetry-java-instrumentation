package datadog.trace.instrumentation.springwebflux.client;

import io.opentracing.util.GlobalTracer;
import net.bytebuddy.asm.Advice;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

public class DefaultWebClientAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
    @Advice.Thrown final Throwable throwable,
    @Advice.This final ExchangeFunction exchangeFunction,
    @Advice.Argument(0) final ClientRequest clientRequest,
    @Advice.Return(readOnly = false) Mono<ClientResponse> mono) {
    if (throwable == null
      && mono != null
      // The response of the org.springframework.web.reactive.function.client.ExchangeFunction.exchange method is
      // replaced by a decorator that in turn also calls the
      // org.springframework.web.reactive.function.client.ExchangeFunction.exchange method. If the original return value
      // is already decorated (we detect this if the "x-datadog-trace-id" is added), the result is not decorated again
      // to avoid StackOverflowErrors.
      && !clientRequest.headers().keySet().contains("x-datadog-trace-id")) {
      mono = new TracingClientResponseMono(clientRequest, exchangeFunction, GlobalTracer.get());
    }
  }
}
