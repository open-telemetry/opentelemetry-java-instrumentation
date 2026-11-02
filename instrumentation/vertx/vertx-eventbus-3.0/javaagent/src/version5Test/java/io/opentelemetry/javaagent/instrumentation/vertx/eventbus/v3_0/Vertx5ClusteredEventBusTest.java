/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.vertx.core.Vertx;
import io.vertx.test.fakecluster.FakeClusterManager;

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
}
