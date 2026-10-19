/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientQueryState.QUERY_STATE;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentSubmission;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientConnectionPoolState.Submission;
import io.vertx.core.Completable;
import io.vertx.core.internal.pool.ConnectionPool;
import io.vertx.core.internal.pool.PoolWaiter;
import io.vertx.sqlclient.internal.command.CommandBase;
import org.junit.jupiter.api.Test;

class VertxSqlClientConnectionPoolStateTest {
  private static final VirtualField<PoolWaiter<?>, VertxSqlClientQueryState> WAITER_QUERY =
      VirtualField.find(PoolWaiter.class, VertxSqlClientQueryState.class);

  @Test
  void handsOffOnlyOnceToTheMatchingPoolAndHandler() {
    ConnectionPool<?> pool = mock(ConnectionPool.class);
    Completable<?> handler = mock(Completable.class);
    VertxSqlClientQueryState query = mock(VertxSqlClientQueryState.class);
    PoolWaiter<?> waiter = mock(PoolWaiter.class);
    PoolWaiter<?> unrelatedWaiter = mock(PoolWaiter.class);
    Submission submission = submission(pool, query);
    Submission previous = currentSubmission().set(submission);
    try {
      assertThat(
              VertxSqlClientConnectionPoolState.beginAcquisition(
                  mock(ConnectionPool.class), handler))
          .isNull();
      VertxSqlClientConnectionPoolState.attachWaiter(unrelatedWaiter, handler);
      assertThat(WAITER_QUERY.get(unrelatedWaiter)).isNull();

      assertThat(VertxSqlClientConnectionPoolState.beginAcquisition(pool, handler))
          .isSameAs(submission);
      VertxSqlClientConnectionPoolState.attachWaiter(unrelatedWaiter, mock(Completable.class));
      assertThat(WAITER_QUERY.get(unrelatedWaiter)).isNull();
      VertxSqlClientConnectionPoolState.attachWaiter(waiter, handler);
      assertThat(WAITER_QUERY.get(waiter)).isSameAs(query);

      VertxSqlClientConnectionPoolState.attachWaiter(unrelatedWaiter, handler);
      assertThat(WAITER_QUERY.get(unrelatedWaiter)).isNull();
      assertThat(VertxSqlClientConnectionPoolState.beginAcquisition(pool, handler)).isNull();
    } finally {
      submission.endAcquisition();
      currentSubmission().restore(previous);
    }
  }

  @Test
  void discardsAnUnconsumedHandoffOnExit() {
    ConnectionPool<?> pool = mock(ConnectionPool.class);
    Completable<?> handler = mock(Completable.class);
    PoolWaiter<?> waiter = mock(PoolWaiter.class);
    Submission submission = submission(pool, mock(VertxSqlClientQueryState.class));
    Submission previous = currentSubmission().set(submission);
    try {
      Submission entered = ConnectionPoolInstrumentation.AcquireAdvice.onEnter(pool, handler);
      assertThat(entered).isSameAs(submission);
      ConnectionPoolInstrumentation.AcquireAdvice.onExit(entered);

      VertxSqlClientConnectionPoolState.attachWaiter(waiter, handler);
      assertThat(WAITER_QUERY.get(waiter)).isNull();
      assertThat(VertxSqlClientConnectionPoolState.beginAcquisition(pool, handler)).isNull();
    } finally {
      currentSubmission().restore(previous);
    }
  }

  @Test
  void keepsNestedSubmissionsIndependent() {
    ConnectionPool<?> pool = mock(ConnectionPool.class);
    Completable<?> handler = mock(Completable.class);
    VertxSqlClientQueryState outerQuery = mock(VertxSqlClientQueryState.class);
    VertxSqlClientQueryState innerQuery = mock(VertxSqlClientQueryState.class);
    Submission outer = submission(pool, outerQuery);
    Submission inner = submission(pool, innerQuery);
    PoolWaiter<?> outerWaiter = mock(PoolWaiter.class);
    PoolWaiter<?> innerWaiter = mock(PoolWaiter.class);
    Submission previous = currentSubmission().set(outer);
    try {
      assertThat(VertxSqlClientConnectionPoolState.beginAcquisition(pool, handler)).isSameAs(outer);
      Submission previousSubmission = currentSubmission().set(inner);
      try {
        assertThat(VertxSqlClientConnectionPoolState.beginAcquisition(pool, handler))
            .isSameAs(inner);
        VertxSqlClientConnectionPoolState.attachWaiter(innerWaiter, handler);
      } finally {
        inner.endAcquisition();
        currentSubmission().restore(previousSubmission);
      }
      VertxSqlClientConnectionPoolState.attachWaiter(outerWaiter, handler);

      assertThat(WAITER_QUERY.get(outerWaiter)).isSameAs(outerQuery);
      assertThat(WAITER_QUERY.get(innerWaiter)).isSameAs(innerQuery);
    } finally {
      outer.endAcquisition();
      currentSubmission().restore(previous);
    }
  }

  private static Submission submission(ConnectionPool<?> pool, VertxSqlClientQueryState query) {
    CommandBase<?> command = mock(CommandBase.class);
    VertxSqlClientSingletons.setCommandContext(command, Context.root().with(QUERY_STATE, query));
    return VertxSqlClientConnectionPoolState.createSubmission(pool, command);
  }
}
