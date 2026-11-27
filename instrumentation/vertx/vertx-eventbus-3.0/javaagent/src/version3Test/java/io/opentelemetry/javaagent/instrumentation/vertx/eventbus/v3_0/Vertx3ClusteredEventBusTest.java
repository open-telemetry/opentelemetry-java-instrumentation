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

@SuppressWarnings("deprecation") // setClustered is deprecated in later 3.x
class Vertx3ClusteredEventBusTest extends Vertx3EventBusTest {

  @Override
  protected Vertx createVertx() throws Exception {
    CompletableFuture<Vertx> result = new CompletableFuture<>();
    VertxOptions options =
        new VertxOptions().setClustered(true).setClusterManager(new FakeClusterManager());
    Vertx.clusteredVertx(
        options,
        ar -> {
          if (ar.succeeded()) {
            result.complete(ar.result());
          } else {
            result.completeExceptionally(ar.cause());
          }
        });
    return result.get(30, SECONDS);
  }

  @Test
  void propagatesToOtherNode() throws Exception {
    Vertx otherNode = createVertx();
    try {
      assertPropagatesToOtherNode(otherNode, Vertx3ClusteredEventBusTest::awaitRegistration);
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
