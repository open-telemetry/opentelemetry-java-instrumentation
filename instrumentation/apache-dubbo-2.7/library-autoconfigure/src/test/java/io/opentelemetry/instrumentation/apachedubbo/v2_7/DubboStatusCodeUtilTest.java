/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboStatusCodeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DubboStatusCodeUtilTest {

  @ParameterizedTest
  @CsvSource({
    "20, OK",
    "30, CLIENT_TIMEOUT",
    "31, SERVER_TIMEOUT",
    "35, CHANNEL_INACTIVE",
    "40, BAD_REQUEST",
    "50, BAD_RESPONSE",
    "60, SERVICE_NOT_FOUND",
    "70, SERVICE_ERROR",
    "80, SERVER_ERROR",
    "90, CLIENT_ERROR",
    "100, SERVER_THREADPOOL_EXHAUSTED_ERROR",
    "120, SERIALIZATION_ERROR"
  })
  void testDubbo2StatusCodeToString(byte statusCode, String expectedName) {
    assertThat(DubboStatusCodeUtil.dubbo2StatusCodeToString(statusCode)).isEqualTo(expectedName);
  }

  @Test
  void testUnknownDubbo2StatusCode() {
    assertThat(DubboStatusCodeUtil.dubbo2StatusCodeToString((byte) 99)).startsWith("UNKNOWN_STATUS_");
  }

  @ParameterizedTest
  @CsvSource({
    "SERVER_ERROR, true",
    "SERVER_THREADPOOL_EXHAUSTED_ERROR, true",
    "SERVER_TIMEOUT, true",
    "SERVICE_ERROR, true",
    "OK, false",
    "CLIENT_TIMEOUT, false",
    "BAD_REQUEST, false",
    "CLIENT_ERROR, false"
  })
  void testIsDubbo2ServerError(String statusCodeName, boolean expectedIsError) {
    assertThat(DubboStatusCodeUtil.isDubbo2ServerError(statusCodeName)).isEqualTo(expectedIsError);
  }

  @ParameterizedTest
  @CsvSource({
    "OK, false",
    "CLIENT_TIMEOUT, true",
    "SERVER_ERROR, true",
    "BAD_REQUEST, true"
  })
  void testIsDubbo2ClientError(String statusCodeName, boolean expectedIsError) {
    assertThat(DubboStatusCodeUtil.isDubbo2ClientError(statusCodeName)).isEqualTo(expectedIsError);
  }

  @ParameterizedTest
  @CsvSource({
    "DATA_LOSS, true",
    "DEADLINE_EXCEEDED, true",
    "INTERNAL, true",
    "UNAVAILABLE, true",
    "UNIMPLEMENTED, true",
    "UNKNOWN, true",
    "OK, false",
    "CANCELLED, false",
    "INVALID_ARGUMENT, false",
    "NOT_FOUND, false",
    "PERMISSION_DENIED, false"
  })
  void testIsTripleServerError(String statusCodeName, boolean expectedIsError) {
    assertThat(DubboStatusCodeUtil.isTripleServerError(statusCodeName)).isEqualTo(expectedIsError);
  }

  @ParameterizedTest
  @CsvSource({
    "OK, false",
    "CANCELLED, true",
    "UNKNOWN, true",
    "DEADLINE_EXCEEDED, true"
  })
  void testIsTripleClientError(String statusCodeName, boolean expectedIsError) {
    assertThat(DubboStatusCodeUtil.isTripleClientError(statusCodeName)).isEqualTo(expectedIsError);
  }
}
