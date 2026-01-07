package io.opentelemetry.instrumentation.annotations.incubator;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;

public class TimedUsageNoOpTest {
  @Test
  void testExampleWithName() {
    new TimedUsageExamples().exampleWithName();
  }

  @Test
  void testExampleWithDescription() {
    new TimedUsageExamples().exampleWithDescription();
  }

  @Test
  void testExampleWithStaticAttributes() {
    new TimedUsageExamples().exampleWithStaticAttributes();
  }

  @Test
  void testExampleWithAttributes() {
    new TimedUsageExamples().exampleWithAttributes("attr1", 2, new TimedUsageExamples.ToStringObject());
  }

  @Test
  void testExampleIgnore() {
    new TimedUsageExamples().exampleIgnore();
  }

  @Test
  void testExampleWithException() {
    try {
      new TimedUsageExamples().exampleWithException();
    } catch (IllegalStateException e) {
      // noop
    }
  }

  @Test
  void testExampleWithReturnNameAttribute() {
    new TimedUsageExamples().exampleWithReturnValueAttribute();
  }

  @Test
  void testCompletableFuture() {
    CompletableFuture<String> future = new CompletableFuture<>();
    new TimedUsageExamples().completableFuture(future);
    future.complete("Done");
  }
}
