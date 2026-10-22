/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JedisRequestContextTest {
  @Test
  void returnedBatchFailuresUseFirstServerError() {
    Throwable firstServerFailure = new IllegalStateException("first server failure");
    Throwable laterServerFailure = new IllegalStateException("later server failure");
    assertBatchCompletion(firstServerFailure, laterServerFailure, null, firstServerFailure);
  }

  @Test
  void terminalFirstServerFailureWins() {
    Throwable firstServerFailure = new IllegalStateException("first server failure");
    Throwable laterServerFailure = new IllegalStateException("later server failure");
    assertBatchCompletion(
        firstServerFailure, laterServerFailure, firstServerFailure, firstServerFailure);
  }

  @Test
  void terminalLaterServerFailureWins() {
    Throwable firstServerFailure = new IllegalStateException("first server failure");
    Throwable laterServerFailure = new IllegalStateException("later server failure");
    assertBatchCompletion(
        firstServerFailure, laterServerFailure, laterServerFailure, laterServerFailure);
  }

  @Test
  void terminalClientFailureWins() {
    Throwable firstServerFailure = new IllegalStateException("first server failure");
    Throwable laterServerFailure = new IllegalStateException("later server failure");
    Throwable clientFailure = new IllegalStateException("WRONGTYPE client failure");
    assertBatchCompletion(firstServerFailure, laterServerFailure, clientFailure, clientFailure);
  }

  @Test
  void serverErrorConfirmationUsesThrowableIdentity() {
    JedisRequestContext<Object> context = JedisRequestContext.attachForBatch();
    Throwable firstServerFailure = new IllegalStateException("WRONGTYPE failure");
    Throwable terminalServerFailure = new IllegalStateException("WRONGTYPE failure");
    JedisRequestContext.captureServerError(firstServerFailure);
    JedisRequestContext.captureServerError(terminalServerFailure);

    assertThat(context.isServerError(firstServerFailure)).isTrue();
    assertThat(context.isServerError(terminalServerFailure)).isTrue();
    assertThat(context.isServerError(new IllegalStateException("WRONGTYPE failure"))).isFalse();
    assertThat(context.detachAndEnd(terminalServerFailure)).isSameAs(terminalServerFailure);
    assertDetached();
  }

  @Test
  void nestedBatchCaptureRestoresEnclosingContext() {
    JedisRequestContext<Object> outerContext = JedisRequestContext.attach();
    assertThat(JedisRequestContext.attach()).isNull();
    Throwable firstServerFailure = new IllegalStateException("first server failure");
    Throwable laterServerFailure = new IllegalStateException("later server failure");
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();

    JedisRequestContext.captureServerError(firstServerFailure);
    JedisRequestContext.captureServerError(laterServerFailure);
    assertThat(batchContext.detachAndEnd(null)).isSameAs(firstServerFailure);
    assertThat(JedisRequestContext.attach()).isNull();

    Throwable nextServerFailure = new IllegalStateException("next server failure");
    batchContext = JedisRequestContext.attachForBatch();
    JedisRequestContext.captureServerError(nextServerFailure);
    Throwable terminalClientFailure = new IllegalStateException("terminal client failure");
    assertThat(batchContext.detachAndEnd(terminalClientFailure)).isSameAs(terminalClientFailure);
    assertThat(JedisRequestContext.attach()).isNull();

    assertThat(outerContext.detachAndEnd(null)).isNull();
    assertDetached();
  }

  @Test
  void nestedCommandCaptureDoesNotFailEnclosingBatch() {
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();
    JedisRequestContext<Object> nestedContext = JedisRequestContext.attach();
    Throwable nestedServerFailure = new IllegalStateException("nested server failure");

    JedisRequestContext.captureServerError(nestedServerFailure);
    assertThat(nestedContext.detachAndEnd(null)).isNull();
    assertThat(batchContext.detachAndEnd(null)).isNull();
    assertDetached();
  }

  @Test
  void nestedTransactionCaptureUsesIdentityAndRestoresEnclosingOwner() {
    Object outerTransaction = new String("transaction");
    JedisRequestContext<Object> outerContext = JedisRequestContext.attachForBatch(outerTransaction);
    try {
      assertThat(JedisRequestContext.attachForTransaction(outerTransaction)).isNull();

      Throwable outerFailure = new IllegalStateException("outer transaction failure");
      JedisRequestContext.captureServerError(outerFailure);
      Object nestedTransaction = new String("transaction");
      JedisRequestContext<Object> nestedContext =
          requireNonNull(JedisRequestContext.attachForTransaction(nestedTransaction));
      try {
        Throwable nestedFailure = new IllegalStateException("nested transaction failure");
        JedisRequestContext.captureServerError(nestedFailure);
        assertThat(nestedContext.detachAndEnd(null)).isSameAs(nestedFailure);
      } finally {
        nestedContext.detachAndEnd(null);
      }

      assertThat(JedisRequestContext.attachForTransaction(outerTransaction)).isNull();
      assertThat(outerContext.detachAndEnd(null)).isSameAs(outerFailure);
    } finally {
      outerContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void propagatesCommandFailure() {
    JedisRequestContext<Object> context = JedisRequestContext.attach();
    Throwable clientFailure = new IllegalStateException("WRONGTYPE client failure");
    try {
      assertThat(context.detachAndEnd(clientFailure)).isSameAs(clientFailure);
    } finally {
      context.detachAndEnd(null);
    }
    assertDetached();
  }

  private static void assertBatchCompletion(
      Throwable firstServerFailure,
      Throwable laterServerFailure,
      Throwable terminalFailure,
      Throwable expectedFailure) {
    JedisRequestContext<Object> context = JedisRequestContext.attachForBatch();
    try {
      JedisRequestContext.captureServerError(firstServerFailure);
      JedisRequestContext.captureServerError(laterServerFailure);
      assertThat(context.detachAndEnd(terminalFailure)).isSameAs(expectedFailure);
    } finally {
      context.detachAndEnd(null);
    }
    assertDetached();
  }

  private static void assertDetached() {
    JedisRequestContext<Object> next = JedisRequestContext.attach();
    try {
      assertThat(next).isNotNull();
      assertThat(next.detachAndEnd(null)).isNull();
    } finally {
      if (next != null) {
        next.detachAndEnd(null);
      }
    }
  }
}
