/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.web.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;

class Vertx41HttpServerTest extends AbstractVertxHttpServerTest {

  @Override
  protected Class<? extends AbstractVerticle> verticle() {
    return Vertx41WebServer.class;
  }

  @Override
  protected void stopServer(Vertx server) throws Exception {
    server.close().toCompletionStage().toCompletableFuture().get(30, SECONDS);
  }
}
