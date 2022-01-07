/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.vertx.core.AbstractVerticle

class VertxLatestHttpServerTest extends AbstractVertxHttpServerTest {

  @Override
  protected Class<? extends AbstractVerticle> verticle() {
    return VertxLatestWebServer
  }
}