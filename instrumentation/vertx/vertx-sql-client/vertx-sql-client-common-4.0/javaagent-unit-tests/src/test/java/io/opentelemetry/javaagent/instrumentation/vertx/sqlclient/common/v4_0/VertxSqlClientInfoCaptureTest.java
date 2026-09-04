/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VertxSqlClientInfoCaptureTest {

  @Test
  void assignsRequestsBeforeConnectionAttemptsComplete() {
    VertxSqlClientInfoCapture capture = new VertxSqlClientInfoCapture();
    Object firstRequest = new Object();
    Object secondRequest = new Object();

    capture.addConnectionRequest(firstRequest);
    capture.addConnectionRequest(secondRequest);

    assertThat(capture.takeConnectionRequest()).isSameAs(firstRequest);
    assertThat(capture.takeConnectionRequest()).isSameAs(secondRequest);
    assertThat(capture.takeConnectionRequest()).isNull();
  }

  @Test
  void removesRequestCompletedWithoutConnectionAttempt() {
    VertxSqlClientInfoCapture capture = new VertxSqlClientInfoCapture();
    Object completedRequest = new Object();
    Object pendingRequest = new Object();

    capture.addConnectionRequest(completedRequest);
    capture.addConnectionRequest(pendingRequest);
    capture.removeConnectionRequest(completedRequest);

    assertThat(capture.takeConnectionRequest()).isSameAs(pendingRequest);
  }
}
