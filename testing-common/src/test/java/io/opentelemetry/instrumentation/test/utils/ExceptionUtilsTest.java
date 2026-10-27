/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExceptionUtilsTest {

  @Test
  void rethrowsOriginalThrowable() {
    Exception exception = new Exception("original");

    assertThatThrownBy(() -> ExceptionUtils.sneakyThrow(exception)).isSameAs(exception);
  }

  @Test
  void rejectsNull() {
    assertThatNullPointerException()
        .isThrownBy(() -> ExceptionUtils.sneakyThrow(null))
        .withMessage("t");
  }
}
