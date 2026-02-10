/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.incubator.timed;

import io.opentelemetry.instrumentation.annotations.incubator.Attribute;
import io.opentelemetry.instrumentation.annotations.incubator.ReturnValueAttribute;
import io.opentelemetry.instrumentation.annotations.incubator.StaticAttribute;
import io.opentelemetry.instrumentation.annotations.incubator.Timed;
import java.util.concurrent.CompletableFuture;

public class TimedExample {
  public static final String METRIC_NAME = "name.duration";
  public static final String METRIC_DESCRIPTION = "I am the description.";
  public static final String TO_STRING = "I am a to string object.";

  @Timed(name = METRIC_NAME)
  public void exampleWithName() {}

  @Timed(name = "example.with.description.duration", description = METRIC_DESCRIPTION)
  public void exampleWithDescription() {}

  @Timed(name = "example.with.static.attributes.duration")
  @StaticAttribute(name = "key1", value = "value1")
  @StaticAttribute(name = "key2", value = "value3")
  @StaticAttribute(name = "key2", value = "value2")
  public void exampleWithStaticAttributes() {}

  @Timed(name = "example.with.attributes.duration")
  public void exampleWithAttributes(
      @Attribute String attribute1,
      @Attribute(name = "custom_attr1") long attribute2,
      @Attribute(name = "custom_attr2") TimedExample.ToStringObject toStringObject) {}

  @Timed(name = "example.ignore.duration")
  public void exampleIgnore() {}

  @Timed(name = "example.with.exception.duration")
  public void exampleWithException() {
    throw new IllegalStateException("test");
  }

  @Timed(name = "example.with.return.duration")
  @ReturnValueAttribute("returnValue")
  public ToStringObject exampleWithReturnValueAttribute() {
    return new ToStringObject();
  }

  @Timed(name = "example.completable.future.duration")
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
