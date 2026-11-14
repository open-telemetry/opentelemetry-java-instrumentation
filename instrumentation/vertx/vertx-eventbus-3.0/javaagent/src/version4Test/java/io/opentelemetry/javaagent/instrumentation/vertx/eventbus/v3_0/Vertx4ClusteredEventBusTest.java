/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.test.fakecluster.FakeClusterManager;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class Vertx4ClusteredEventBusTest extends Vertx4EventBusTest {

  @Override
  protected Vertx createVertx() throws Exception {
    VertxOptions options = new VertxOptions().setClusterManager(new FakeClusterManager());
    return Vertx.clusteredVertx(options).toCompletionStage().toCompletableFuture().get(30, SECONDS);
  }

  @Test
  void propagatesToOtherNode() throws Exception {
    Vertx otherNode = createVertx();
    try {
      assertPropagatesToOtherNode(otherNode, Vertx4ClusteredEventBusTest::awaitRegistration);
    } finally {
      closeVertx(otherNode);
    }
  }

  private static void awaitRegistration(MessageConsumer<Object> consumer) {
    CompletableFuture<Void> registered = new CompletableFuture<>();
    consumer.completionHandler(ar -> registered.complete(null));
    registered.join();
  }
}
