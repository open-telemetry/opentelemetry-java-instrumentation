package datadog.trace.instrumentation.springwebflux;

import java.util.function.BiConsumer;
import org.springframework.web.server.ServerWebExchange;

public class DispatcherHandlerOnSuccessOrError<U> implements BiConsumer<U, Throwable> {

  private final ServerWebExchange exchange;

  public DispatcherHandlerOnSuccessOrError(final ServerWebExchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public void accept(final U object, final Throwable throwable) {
    // Closing span here means it closes after Netty span which may not be ideal.
    // We could instrument HandlerFunctionAdapter instead, but this would mean we
    // would not account for time spent sending request.
    AdviceUtils.finishSpanIfPresent(exchange, throwable);
  }
}
