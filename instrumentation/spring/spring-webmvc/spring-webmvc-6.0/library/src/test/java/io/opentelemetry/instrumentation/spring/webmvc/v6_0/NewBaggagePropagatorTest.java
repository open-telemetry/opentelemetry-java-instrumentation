package io.opentelemetry.instrumentation.spring.webmvc.v6_0;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class NewBaggagePropagatorTest {
  @Test
  void testBaggageMultipleHeaders() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.getInstance();

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("baggage", "k1=v1");
    request.addHeader("baggage", "k2=v2");

    Context result =
        propagator.extract(
            Context.root(),
            request,
            JakartaHttpServletRequestGetter.INSTANCE);

    Baggage expectedBaggage = Baggage.builder().put("k1", "v1").put("k2", "v2").build();
    assertThat(Baggage.fromContext(result)).isEqualTo(expectedBaggage);
  }
}

