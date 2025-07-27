/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.incubator.counted;

import io.opentelemetry.instrumentation.annotations.incubator.Attribute;
import io.opentelemetry.instrumentation.annotations.incubator.Counted;
import io.opentelemetry.instrumentation.annotations.incubator.ReturnValueAttribute;
import io.opentelemetry.instrumentation.annotations.incubator.StaticAttribute;
import java.util.concurrent.CompletableFuture;

public class CountedExample {

  public static final String METRIC_NAME = "name.count";
  public static final String METRIC_DESCRIPTION = "I am the description.";
  public static final String METRIC_UNIT = "ms";
  public static final String TO_STRING = "I am a to string object.";

  @Counted(name = METRIC_NAME)
  public void exampleWithName() {}

  @Counted(name = "example.with.description.count", description = METRIC_DESCRIPTION)
  public void exampleWithDescription() {}

  @Counted(name = "example.with.unit.count", unit = METRIC_UNIT)
  public void exampleWithUnit() {}

  @Counted(name = "example.with.static.attributes.count")
  @StaticAttribute(name = "key1", value = "value1")
  @StaticAttribute(name = "key2", value = "value3")
  @StaticAttribute(name = "key2", value = "value2")
  public void exampleWithStaticAttributes() {}

  @Counted(name = "example.with.attributes.count")
  public void exampleWithAttributes(
      @Attribute String attribute1,
      @Attribute(name = "custom_attr1") long attribute2,
      @Attribute(name = "custom_attr2") ToStringObject toStringObject) {}

  @Counted(name = "example.with.return.count")
  @ReturnValueAttribute("returnValue")
  public ToStringObject exampleWithReturnValueAttribute() {
    return new ToStringObject();
  }

  @Counted(name = "example.with.exception.count")
  public void exampleWithException() {
    throw new IllegalStateException("test exception.");
  }

  @Counted(name = "example.ignore.count")
  public void exampleIgnore() {}

  @Counted(name = "example.completable.future.count")
  @ReturnValueAttribute("returnValue")
  public CompletableFuture<String> completableFuture(CompletableFuture<String> future) {
    return future;
  }

  public static class ToStringObject {
    @Override
    public String toString() {
      return TO_STRING;
    }
  }
}
