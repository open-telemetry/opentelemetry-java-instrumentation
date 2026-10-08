/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26;

import static io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26.RedissonSingletons.INSTRUMENTATION_NAME;

import io.opentelemetry.javaagent.instrumentation.redissonmetrics.AbstractRedissonConnectionPoolMetricsTest;
import java.util.concurrent.CompletableFuture;
import org.redisson.Redisson;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionsHolder;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.AsyncSemaphore;

class RedissonConnectionPoolMetricsTest extends AbstractRedissonConnectionPoolMetricsTest {

  @Override
  protected String instrumentationName() {
    return INSTRUMENTATION_NAME;
  }

  @Override
  protected void assertDynamicPoolMetrics(
      Redisson redisson, String regularPool, String subscriptionPool)
      throws ReflectiveOperationException {
    MasterSlaveEntry entry = getMasterSlaveEntry(redisson);
    ClientConnectionsEntry masterEntry = getMasterConnectionsEntry(entry);
    ConnectionsHolder<RedisConnection> holder = masterEntry.getConnectionsHolder();
    AsyncSemaphore semaphore = holder.getFreeConnectionsCounter();

    clearMetrics();
    RedisConnection connection = entry.connectionWriteOp(RedisCommands.PING).join();
    try {
      // Delta temporality reports one fewer idle connection and one used connection.
      assertUsageMetric(regularPool, -1, 1, subscriptionPool, 0, 0);
    } finally {
      entry.releaseWrite(connection);
    }

    clearMetrics();
    for (int i = 0; i < REGULAR_MAX; i++) {
      semaphore.acquire().join();
    }
    CompletableFuture<Void> queued = semaphore.acquire();
    try {
      assertPendingRequests(regularPool, 1, subscriptionPool, 0);
    } finally {
      for (int i = 0; i <= REGULAR_MAX; i++) {
        semaphore.release();
      }
    }
    queued.join();
  }
}
