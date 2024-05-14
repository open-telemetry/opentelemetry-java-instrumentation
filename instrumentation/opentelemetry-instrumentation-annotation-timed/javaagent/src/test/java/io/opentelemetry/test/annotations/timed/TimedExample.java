/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotations.timed;

import io.opentelemetry.instrumentation.annotations.Timed;
import java.util.concurrent.TimeUnit;

public class TimedExample {
  public static final String ANOTHER_NAME_HISTOGRAM = "another.name.duration";
  public static final String METRIC_DESCRIPTION = "I am the description.";
  public static final String RETURN_STRING = "I am a return string.";

  @Timed
  public void defaultExample() {}

  @Timed(ANOTHER_NAME_HISTOGRAM)
  public void exampleWithAnotherName() {}

  @Timed(description = METRIC_DESCRIPTION)
  public void exampleWithDescriptionAndDefaultValue() {}

  @Timed(unit = TimeUnit.MICROSECONDS)
  public void exampleWithUnitUSAndDefaultValue() {}

  @Timed(value = "example.with.description.duration", description = METRIC_DESCRIPTION)
  public void exampleWithDescription() {}

  @Timed(value = "example.with.unit.duration", unit = TimeUnit.SECONDS)
  public void exampleWithUnitSecondAnd2SecondLatency() throws InterruptedException {
    Thread.sleep(2000);
  }

  @Timed(additionalAttributes = {"key1", "value1", "key2", "value2"})
  public void exampleWithAdditionalAttributes1() {}

  @Timed(additionalAttributes = {"key1", "value1", "key2", "value2", "key3"})
  public void exampleWithAdditionalAttributes2() {}

  @Timed
  public void exampleIgnore() {}

  @Timed
  public void exampleWithException() {
    throw new IllegalStateException("test");
  }

  @Timed(returnValueAttribute = "returnValue")
  public ReturnObject exampleWithReturnValueAttribute() {
    return new ReturnObject();
  }

  public static class ReturnObject {
    @Override
    public String toString() {
      return RETURN_STRING;
    }
  }
}
