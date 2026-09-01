/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VertxSqlClientDataCaptureTest {

  @Test
  void assignsRequestsBeforeConnectionAttemptsComplete() {
    VertxSqlClientDataCapture capture = new VertxSqlClientDataCapture();
    Object firstRequest = new Object();
    Object secondRequest = new Object();

    capture.addConnectionRequest(firstRequest);
    capture.addConnectionRequest(secondRequest);
    Object firstAttempt = capture.takeConnectionRequest();
    Object secondAttempt = capture.takeConnectionRequest();

    assertThat(secondAttempt).isSameAs(secondRequest);
    assertThat(firstAttempt).isSameAs(firstRequest);
    assertThat(capture.takeConnectionRequest()).isNull();
  }

  @Test
  void removesRequestCompletedWithoutAConnectionAttempt() {
    VertxSqlClientDataCapture capture = new VertxSqlClientDataCapture();
    Object completedRequest = new Object();
    Object pendingRequest = new Object();

    capture.addConnectionRequest(completedRequest);
    capture.addConnectionRequest(pendingRequest);
    capture.removeConnectionRequest(completedRequest);

    assertThat(capture.takeConnectionRequest()).isSameAs(pendingRequest);
  }
}
