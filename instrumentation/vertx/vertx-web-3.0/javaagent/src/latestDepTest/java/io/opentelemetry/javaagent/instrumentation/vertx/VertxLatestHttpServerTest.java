/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;

class VertxLatestHttpServerTest extends AbstractVertxHttpServerTest {

  @Override
  protected Class<? extends AbstractVerticle> verticle() {
    return VertxLatestWebServer.class;
  }

  @Override
  protected void stopServer(Vertx server) throws Exception {
    server.close();
  }
}
