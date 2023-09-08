package io.opentelemetry.javaagent.tooling;

import static org.mockito.Mockito.verify;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddBaggageSpanProcessorTest {
  @Mock ReadWriteSpan readWriteSpan;

  @Test
  void shouldAddBaggageToSpanWithPrefixedKeysWhenBaggageIsPopulated() {
    try (Scope unused = Baggage.builder().put("someKey", "someValue").build().makeCurrent()) {
      new AddBaggageSpanProcessor().onStart(Context.current(), readWriteSpan);
    }

    verify(readWriteSpan).setAttribute("baggage.someKey", "someValue");
  }
}
