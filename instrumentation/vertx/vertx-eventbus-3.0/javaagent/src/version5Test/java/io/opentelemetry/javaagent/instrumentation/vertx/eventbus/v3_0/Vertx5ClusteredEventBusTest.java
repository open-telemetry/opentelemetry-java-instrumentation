/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.jupiter.api.Test;

class Vertx5ClusteredEventBusTest extends Vertx5EventBusTest {

  @Override
  protected Vertx createVertx() throws Exception {
    return Vertx.builder()
        .withClusterManager(new FakeClusterManager())
        .buildClustered()
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);
  }

  @Test
  void propagatesToOtherNode() throws Exception {
    Vertx otherNode = createVertx();
    try {
      assertPropagatesToOtherNode(otherNode, Vertx5ClusteredEventBusTest::awaitRegistration);
    } finally {
      closeVertx(otherNode);
    }
  }

  private static void awaitRegistration(MessageConsumer<Object> consumer) {
    consumer.completion().toCompletionStage().toCompletableFuture().join();
  }
}
