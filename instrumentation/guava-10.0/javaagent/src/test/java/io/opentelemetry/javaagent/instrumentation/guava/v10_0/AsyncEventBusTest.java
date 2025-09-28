package io.opentelemetry.javaagent.instrumentation.guava.v10_0;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.Subscribe;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncEventBusTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static final ExecutorService executor = Executors.newSingleThreadExecutor();

  static final AsyncEventBus asyncEventBus = new AsyncEventBus(executor);

  @Test
  void testAsyncEventBusTakeEffect() {
    class Listener {
      String receivedTraceId;

      @Subscribe
      public void onEvent(String event) {
        testing.runWithSpan("listener", () -> {
          receivedTraceId = Span.current().getSpanContext().getTraceId();
        });
      }
    }

    Listener listener = new Listener();
    asyncEventBus.register(listener);

    String[] parentTraceId = new String[1];
    testing.runWithSpan("parent", () -> {
      parentTraceId[0] = Span.current().getSpanContext().getTraceId();
      asyncEventBus.post("test");
    });

    testing.waitAndAssertTraces(
        trace
            -> trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
            span -> span.hasName("listener")
        ));

    assertThat(listener.receivedTraceId)
        .isNotNull()
        .isNotEqualTo("00000000000000000000000000000000")
        .isEqualTo(parentTraceId[0]);
  }
}
