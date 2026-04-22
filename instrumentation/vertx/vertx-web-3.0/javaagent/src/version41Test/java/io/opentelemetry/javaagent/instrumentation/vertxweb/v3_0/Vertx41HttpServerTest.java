/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertxweb.v3_0;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;

class Vertx41HttpServerTest extends AbstractVertxHttpServerTest {

  @Override
  protected Class<? extends AbstractVerticle> verticle() {
    return Vertx41WebServer.class;
  }

  @Override
  protected void stopServer(Vertx server) throws Exception {
    server.close();
  }
}
