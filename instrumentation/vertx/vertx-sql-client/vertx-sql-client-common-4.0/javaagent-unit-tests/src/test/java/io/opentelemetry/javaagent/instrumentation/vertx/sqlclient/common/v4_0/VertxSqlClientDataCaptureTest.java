/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.sqlclient.SqlConnectOptions;
import org.junit.jupiter.api.Test;

class VertxSqlClientDataCaptureTest {

  @Test
  void keepsReusedFailureTargetsWithinTheirClient() {
    RuntimeException failure = new RuntimeException("failed");
    VertxSqlClientDataCapture firstCapture = new VertxSqlClientDataCapture();
    VertxSqlClientDataCapture secondCapture = new VertxSqlClientDataCapture();
    VertxSqlClientData firstData = data("first.example");
    VertxSqlClientData secondData = data("second.example");

    firstCapture.addFailureData(failure, firstData);
    secondCapture.addFailureData(failure, secondData);

    assertThat(firstCapture.takeFailureData(failure)).isSameAs(firstData);
    assertThat(secondCapture.takeFailureData(failure)).isSameAs(secondData);
  }

  @Test
  void keepsConcurrentFailureTargetsInCaptureOrder() {
    RuntimeException failure = new RuntimeException("failed");
    VertxSqlClientDataCapture capture = new VertxSqlClientDataCapture();
    VertxSqlClientData firstData = data("first.example");
    VertxSqlClientData secondData = data("second.example");

    capture.addFailureData(failure, firstData);
    capture.addFailureData(failure, secondData);

    assertThat(capture.takeFailureData(failure)).isSameAs(firstData);
    assertThat(capture.takeFailureData(failure)).isSameAs(secondData);
    assertThat(capture.takeFailureData(failure)).isNull();
  }

  private static VertxSqlClientData data(String host) {
    return VertxSqlClientData.fromConnectOptions(
        new SqlConnectOptions().setHost(host).setPort(5432), "postgresql");
  }
}
