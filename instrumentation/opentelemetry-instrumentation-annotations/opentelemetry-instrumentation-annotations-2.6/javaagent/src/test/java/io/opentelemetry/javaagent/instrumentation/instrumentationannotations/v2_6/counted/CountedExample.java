/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6.counted;

import io.opentelemetry.instrumentation.annotations.Counted;
import java.util.concurrent.CompletableFuture;

public class CountedExample {

  public static final String METRIC_NAME = "name.count";
  public static final String METRIC_DESCRIPTION = "I am the description.";
  public static final String METRIC_UNIT = "ms";
  public static final String RETURN_STRING = "I am a return string.";

  @Counted(METRIC_NAME)
  public void exampleWithName() {}

  @Counted(value = "example.with.description.count", description = METRIC_DESCRIPTION)
  public void exampleWithDescription() {}

  @Counted(value = "example.with.unit.count", unit = METRIC_UNIT)
  public void exampleWithUnit() {}

  @Counted(
      value = "example.with.attributes.count",
      additionalAttributes = {"key1", "value1", "key2", "value2"})
  public void exampleWithAdditionalAttributes1() {}

  @Counted(
      value = "example.with.attributes2.count",
      additionalAttributes = {"key1", "value1", "key2", "value2", "key3"})
  public void exampleWithAdditionalAttributes2() {}

  @Counted(value = "example.with.return.count", returnValueAttribute = "returnValue")
  public ReturnObject exampleWithReturnValueAttribute() {
    return new ReturnObject();
  }

  @Counted("example.with.exception.count")
  public void exampleWithException() {
    throw new IllegalStateException("test exception.");
  }

  @Counted("example.ignore.count")
  public void exampleIgnore() {}

  @Counted(value = "example.completable.future.count", returnValueAttribute = "returnValue")
  public CompletableFuture<String> completableFuture(CompletableFuture<String> future) {
    return future;
  }

  public static class ReturnObject {
    @Override
    public String toString() {
      return RETURN_STRING;
    }
  }
}
