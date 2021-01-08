package io.opentelemetry.javaagent.tooling;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PropagatorsInitializerTest {

  @Test
  void initialize_noIdsPassedNotPreconfigured() {
    List<String> ids = emptyList();
    AtomicReference<ContextPropagators> seen = new AtomicReference<>();
    Consumer<ContextPropagators> setter = seen::set;

    PropagatorsInitializer.initializePropagators(ids, TextMapPropagator::noop, setter);

    assertThat(seen.get().getTextMapPropagator())
        .extracting("textPropagators")
        .isInstanceOfSatisfying(TextMapPropagator[].class, p -> assertThat(p).containsExactlyInAnyOrder(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance()
        ));
  }

  @Test
  void initialize_noIdsPassedWithPreconfigured() {
    List<String> ids = emptyList();
    AtomicReference<ContextPropagators> seen = new AtomicReference<>();
    Consumer<ContextPropagators> setter = seen::set;
    TextMapPropagator mockPropagator = mock(TextMapPropagator.class);
    Supplier<TextMapPropagator> preconfigured = () -> mockPropagator;

    PropagatorsInitializer.initializePropagators(ids, preconfigured, setter);

    assertThat(seen.get().getTextMapPropagator())
        .extracting("textPropagators")
        .isInstanceOfSatisfying(TextMapPropagator[].class, p -> assertThat(p).containsExactlyInAnyOrder(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance(),
            mockPropagator
        ));
  }

  @Test
  void initialize_preconfiguredSameAsId() {
    List<String> ids = singletonList("jaeger");
    AtomicReference<ContextPropagators> seen = new AtomicReference<>();
    Consumer<ContextPropagators> setter = seen::set;
    Supplier<TextMapPropagator> preconfigured = () -> PropagatorsInitializer.Propagator.JAEGER;

    PropagatorsInitializer.initializePropagators(ids, preconfigured, setter);

    assertThat(seen.get().getTextMapPropagator()).isSameAs(PropagatorsInitializer.Propagator.JAEGER);
  }

  @Test
  void initialize_preconfiguredDuplicatedInIds() {
    List<String> ids = Arrays.asList("b3", "jaeger", "b3");
    AtomicReference<ContextPropagators> seen = new AtomicReference<>();
    Consumer<ContextPropagators> setter = seen::set;
    Supplier<TextMapPropagator> preconfigured = () -> PropagatorsInitializer.Propagator.JAEGER;

    PropagatorsInitializer.initializePropagators(ids, preconfigured, setter);

    assertThat(seen.get().getTextMapPropagator())
        .extracting("textPropagators")
        .isInstanceOfSatisfying(TextMapPropagator[].class, p -> assertThat(p).containsExactlyInAnyOrder(
            PropagatorsInitializer.Propagator.B3,
            PropagatorsInitializer.Propagator.JAEGER
        ));
  }

  @Test
  void initialize_justOneId() {
    List<String> ids = singletonList("jaeger");
    AtomicReference<ContextPropagators> seen = new AtomicReference<>();
    Consumer<ContextPropagators> setter = seen::set;
    Supplier<TextMapPropagator> preconfigured = TextMapPropagator::noop;

    PropagatorsInitializer.initializePropagators(ids, preconfigured, setter);

    assertThat(seen.get().getTextMapPropagator()).isSameAs(PropagatorsInitializer.Propagator.JAEGER);
  }

  @Test
  void initialize_idsWithNoPreconfigured() {
    List<String> ids = Arrays.asList("b3", "unknown-but-no-harm-done", "jaeger");
    AtomicReference<ContextPropagators> seen = new AtomicReference<>();
    Consumer<ContextPropagators> setter = seen::set;
    Supplier<TextMapPropagator> preconfigured = TextMapPropagator::noop;

    PropagatorsInitializer.initializePropagators(ids, preconfigured, setter);

    assertThat(seen.get().getTextMapPropagator())
        .extracting("textPropagators")
        .isInstanceOfSatisfying(TextMapPropagator[].class, p -> assertThat(p).containsExactlyInAnyOrder(PropagatorsInitializer.Propagator.B3, PropagatorsInitializer.Propagator.JAEGER));
  }

  @Test
  void initialize_idsAndPreconfigured() {
    List<String> ids = Arrays.asList("jaeger", "xray");
    AtomicReference<ContextPropagators> seen = new AtomicReference<>();
    Consumer<ContextPropagators> setter = seen::set;
    TextMapPropagator mockPreconfigured = mock(TextMapPropagator.class);
    when(mockPreconfigured.fields()).thenReturn(singletonList("mocked"));
    Supplier<TextMapPropagator> preconfigured = () -> mockPreconfigured;

    PropagatorsInitializer.initializePropagators(ids, preconfigured, setter);

    assertThat(seen.get().getTextMapPropagator())
        .extracting("textPropagators")
        .isInstanceOfSatisfying(TextMapPropagator[].class, p -> assertThat(p).containsExactlyInAnyOrder(
            mockPreconfigured,
            PropagatorsInitializer.Propagator.JAEGER,
            PropagatorsInitializer.Propagator.XRAY
        ));
  }
}