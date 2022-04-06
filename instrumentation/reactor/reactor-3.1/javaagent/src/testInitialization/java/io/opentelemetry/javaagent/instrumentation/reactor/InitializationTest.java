package io.opentelemetry.javaagent.instrumentation.reactor;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.ContextKey;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import reactor.core.Scannable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;

//
class InitializationTest {

  private static final ContextKey<String> ANIMAL = ContextKey.named("animal");

  @Test
  void contextPropagated() {
    AtomicReference<String> capturedAnimal = new AtomicReference<>();

    UnicastProcessor<String> source1 = UnicastProcessor.create();
    Mono<String> mono1 = source1.singleOrEmpty();
    source1.onNext("foo");
    source1.onComplete();
    mono1.block();

    List<Scannable> parents1 = ((Scannable) mono1).parents().collect(Collectors.toList());

    UnicastProcessor<String> source2 = UnicastProcessor.create();
    Mono<String> mono2 = source2.singleOrEmpty();

    List<Scannable> parents2 = ((Scannable) mono1).parents().collect(Collectors.toList());
    assertThat(parents1).isNotEmpty();
  }
}
