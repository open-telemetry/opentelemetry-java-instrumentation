/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.incubator.timed;

import io.opentelemetry.instrumentation.annotations.incubator.Attribute;
import io.opentelemetry.instrumentation.annotations.incubator.AttributeForReturnValue;
import io.opentelemetry.instrumentation.annotations.incubator.StaticAttribute;
import io.opentelemetry.instrumentation.annotations.incubator.Timed;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TimedExample {
  public static final String METRIC_NAME = "name.duration";
  public static final String METRIC_DESCRIPTION = "I am the description.";
  public static final String TO_STRING = "I am a return string.";

  @Timed(METRIC_NAME)
  public void exampleWithName() {}

  @Timed(value = "example.with.description.duration", description = METRIC_DESCRIPTION)
  public void exampleWithDescription() {}

  @Timed(value = "example.with.unit.duration", unit = TimeUnit.MILLISECONDS)
  public void exampleWithUnit() throws InterruptedException {
    Thread.sleep(2000);
  }

  @Timed("example.with.static.attributes.duration")
  @StaticAttribute(name = "key1", value = "value1")
  @StaticAttribute(name = "key2", value = "value2")
  public void exampleWithStaticAttributes() {}

  @Timed("example.with.attributes.duration")
  public void exampleWithAttributes(
      @Attribute String attribute1,
      @Attribute("custom_attr") long attribute2,
      @Attribute("custom_attr2") TimedExample.ToStringObject toStringObject) {}

  @Timed("example.ignore.duration")
  public void exampleIgnore() {}

  @Timed("example.with.exception.duration")
  public void exampleWithException() {
    throw new IllegalStateException("test");
  }

  @Timed(value = "example.with.return.duration")
  @AttributeForReturnValue("returnValue")
  public ToStringObject exampleWithReturnValueAttribute() {
    return new ToStringObject();
  }

  @Timed(value = "example.completable.future.duration")
  @AttributeForReturnValue("returnValue")
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
