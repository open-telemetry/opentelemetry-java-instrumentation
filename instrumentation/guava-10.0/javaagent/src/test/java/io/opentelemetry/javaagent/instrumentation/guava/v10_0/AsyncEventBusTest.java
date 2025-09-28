package io.opentelemetry.javaagent.instrumentation.guava.v10_0;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.Subscribe;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncEventBusTest {

  static final ExecutorService executor = Executors.newSingleThreadExecutor();

  static final AsyncEventBus asyncEventBus = new AsyncEventBus(executor);

  @Test
  void testAsyncEventBusTakeEffect() {
    String traceId = Span.current().getSpanContext().getTraceId();

    class EventListener {
      @Subscribe
      public void handleEvent(String event) {
        String eventTraceId = Span.current().getSpanContext().getTraceId();
        assertThat(eventTraceId).isNotEqualTo("00000000000000000000000000000000");
        assertThat(eventTraceId).isEqualTo(traceId);
      }
    }

    asyncEventBus.register(new EventListener());
    asyncEventBus.post("Hello, AsyncEventBus!");
  }
}
