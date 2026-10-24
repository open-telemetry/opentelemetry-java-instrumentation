/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.clickhouse.client.ClickHouseException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClickHouseErrorCodeExtractorTest {

  @ParameterizedTest
  @ValueSource(ints = {60, 159, 210, 394, 1002})
  void keepsServerCodesEvenWhenTheyOverlapClientCodes(int code) {
    String message = "Code: " + code + ". DB::Exception: test";
    ClickHouseException response = ClickHouseException.of(message, null);
    ClickHouseException wrappedResponse = ClickHouseException.of(new IOException(message), null);

    assertThat(ClickHouseErrorCodeExtractor.getDbResponseStatusCode(response))
        .isEqualTo(Integer.toString(code));
    assertThat(ClickHouseErrorCodeExtractor.getDbResponseStatusCode(wrappedResponse))
        .isEqualTo(Integer.toString(code));
  }

  @ParameterizedTest
  @MethodSource("clientFailures")
  void omitsClientCodesWithoutChangingErrorType(
      ClickHouseException error, String expectedErrorType) {
    assertThat(ClickHouseErrorCodeExtractor.getErrorType(error)).isEqualTo(expectedErrorType);
    assertThat(ClickHouseErrorCodeExtractor.getDbResponseStatusCode(error)).isNull();
  }

  private static Stream<Arguments> clientFailures() {
    return Stream.of(
        argumentSet(
            "connection failure",
            ClickHouseException.of(new ConnectException("refused"), null),
            "210"),
        argumentSet(
            "unknown host",
            ClickHouseException.of(new UnknownHostException("unknown"), null),
            "210"),
        argumentSet(
            "socket timeout",
            ClickHouseException.of(new SocketTimeoutException("timed out"), null),
            "159"),
        argumentSet(
            "execution timeout",
            ClickHouseException.of(new TimeoutException("timed out"), null),
            "159"),
        argumentSet(
            "cancellation",
            ClickHouseException.forCancellation(new CancellationException("cancelled"), null),
            "236"),
        argumentSet(
            "interruption",
            ClickHouseException.forCancellation(new InterruptedException("interrupted"), null),
            "236"),
        argumentSet(
            "cancelled code",
            new ClickHouseException(394, new CancellationException("cancelled"), null),
            "394"),
        argumentSet(
            "unknown client failure",
            ClickHouseException.of(new IOException("connection closed"), null),
            "1002"));
  }

  @Test
  void omitsUnavailableCodes() {
    assertThat(ClickHouseErrorCodeExtractor.getDbResponseStatusCode(null)).isNull();
    assertThat(
            ClickHouseErrorCodeExtractor.getDbResponseStatusCode(new IllegalStateException("test")))
        .isNull();
    assertThat(
            ClickHouseErrorCodeExtractor.getDbResponseStatusCode(
                new ClickHouseException(0, "test", null)))
        .isNull();
  }
}
