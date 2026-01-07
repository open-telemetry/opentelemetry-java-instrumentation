/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotations.incubator;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

public class CountedUsageNoOpTest {
  @Test
  void testExampleWithAnotherNameNoOp() {
    new CountedUsageExamples().exampleWithName();
  }

  @Test
  void testExampleWithDescriptionNoOp() {
    new CountedUsageExamples().exampleWithDescription();
  }

  @Test
  void testExampleWithUnitNoOp() {
    new CountedUsageExamples().exampleWithUnit();
  }

  @Test
  void testExampleWithStaticAttributesNoOp() {
    new CountedUsageExamples().exampleWithStaticAttributes();
  }

  @Test
  void testExampleWithAttributesNoOp() {
    new CountedUsageExamples()
        .exampleWithAttributes("attr1", 2, new CountedUsageExamples.ToStringObject());
  }

  @Test
  void testExampleWithReturnAttributeNoOp() {
    new CountedUsageExamples().exampleWithReturnValueAttribute();
  }

  @Test
  void testExampleWithExceptionNoOp() {
    try {
      new CountedUsageExamples().exampleWithException();
    } catch (IllegalStateException e) {
      // noop
    }
  }

  @Test
  void testExampleIgnoreNoOp() {
    new CountedUsageExamples().exampleIgnore();
  }

  @Test
  void testCompletableFutureNoOp() {
    CompletableFuture<String> future = new CompletableFuture<>();
    new CountedUsageExamples().completableFuture(future);
    future.complete("Done");
  }
}
