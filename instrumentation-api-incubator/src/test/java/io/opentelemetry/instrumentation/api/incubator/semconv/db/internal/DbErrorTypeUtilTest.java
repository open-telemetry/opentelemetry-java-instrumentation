/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbErrorTypeUtil.fromErrorCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DbErrorTypeUtilTest {

  @ParameterizedTest
  @MethodSource("errorCodes")
  void normalizesVendorCode(int errorCode, String expectedErrorType) {
    assertThat(fromErrorCode(errorCode)).isEqualTo(expectedErrorType);
  }

  private static Stream<Arguments> errorCodes() {
    return Stream.of(
        argumentSet("positive", 42, "42"),
        argumentSet("negative", -42, "-42"),
        argumentSet("zero is unavailable", 0, null));
  }
}
