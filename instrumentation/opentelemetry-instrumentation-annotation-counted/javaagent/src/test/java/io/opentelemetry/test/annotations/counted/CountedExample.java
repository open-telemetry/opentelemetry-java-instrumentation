/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotations.counted;

import io.opentelemetry.instrumentation.annotations.Counted;

public class CountedExample {

  public static final String ANOTHER_NAME_COUNT = "another.name.count";
  public static final String METRIC_DESCRIPTION = "I am the description.";
  public static final String METRIC_UNIT = "ms";
  public static final String RETURN_STRING = "I am a return string.";

  @Counted
  public void defaultExample() {}

  @Counted(ANOTHER_NAME_COUNT)
  public void exampleWithAnotherName() {}

  @Counted(description = METRIC_DESCRIPTION)
  public void exampleWithDescriptionAndDefaultValue() {}

  @Counted(unit = METRIC_UNIT)
  public void exampleWithUnitAndDefaultValue() {}

  @Counted(value = "example.with.description.count", description = METRIC_DESCRIPTION)
  public void exampleWithDescription() {}

  @Counted(value = "example.with.unit.count", unit = METRIC_UNIT)
  public void exampleWithUnit() {}

  @Counted(additionalAttributes = {"key1", "value1", "key2", "value2"})
  public void exampleWithAdditionalAttributes1() {}

  @Counted(additionalAttributes = {"key1", "value1", "key2", "value2", "key3"})
  public void exampleWithAdditionalAttributes2() {}

  @Counted(returnValueAttribute = "returnValue")
  public ReturnObject exampleWithReturnValueAttribute() {
    return new ReturnObject();
  }

  @Counted
  public void exampleWithException() {
    throw new IllegalStateException("test exception.");
  }

  @Counted
  public void exampleIgnore() {}

  public static class ReturnObject {
    @Override
    public String toString() {
      return RETURN_STRING;
    }
  }
}
