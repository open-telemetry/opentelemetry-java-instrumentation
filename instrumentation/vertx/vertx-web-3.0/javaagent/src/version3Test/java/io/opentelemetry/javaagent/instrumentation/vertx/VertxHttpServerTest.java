/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.vertx.core.AbstractVerticle;

class VertxHttpServerTest extends AbstractVertxHttpServerTest {
  @Override
  protected Class<? extends AbstractVerticle> verticle() {
    return VertxWebServer.class;
  }
}
