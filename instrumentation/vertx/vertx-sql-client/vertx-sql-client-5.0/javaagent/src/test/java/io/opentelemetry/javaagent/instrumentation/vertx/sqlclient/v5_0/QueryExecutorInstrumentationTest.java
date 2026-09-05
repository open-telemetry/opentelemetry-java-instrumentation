/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.QueryExecutorInstrumentation.QueryAdvice.AdviceScope.ConnectionInfoState;
import org.junit.jupiter.api.Test;

class QueryExecutorInstrumentationTest {

  @Test
  void defersTerminalCleanupForReentrantConnectionInfoFailure() {
    ConnectionInfoState state = new ConnectionInfoState();
    IllegalStateException error = new IllegalStateException("failure");

    assertThat(state.startConnectionInfo()).isTrue();
    assertThat(state.claimTerminal(error)).isFalse();
    assertThat(state.startConnectionInfo()).isFalse();
    assertThat(state.finishConnectionInfo()).isSameAs(error);
    assertThat(state.claimTerminal(new IllegalStateException("duplicate"))).isFalse();
  }

  @Test
  void claimsTerminalCleanupWhenNoConnectionInfoUpdateIsActive() {
    ConnectionInfoState state = new ConnectionInfoState();

    assertThat(state.claimTerminal(new IllegalStateException("failure"))).isTrue();
    assertThat(state.claimTerminal(new IllegalStateException("duplicate"))).isFalse();
    assertThat(state.startConnectionInfo()).isFalse();
  }
}
