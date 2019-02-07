package datadog.trace.instrumentation.springwebflux;

import org.springframework.web.server.ServerWebExchange;

public class DispatcherHandlerOnCancel implements Runnable {

  private final ServerWebExchange exchange;

  public DispatcherHandlerOnCancel(final ServerWebExchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public void run() {
    // Make sure we are not leaking opened spans for canceled Monos.
    AdviceUtils.finishSpanIfPresent(exchange, null);
  }
}
