/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.test.fakecluster.FakeClusterManager;
import java.util.concurrent.CompletableFuture;

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
}
