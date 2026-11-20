/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

class Vertx4NativeTracingTest extends AbstractVertxNativeTracingTest {

  @Override
  protected Vertx createVertxWithTracer() {
    return Vertx.vertx(
        new VertxOptions()
            .setTracingOptions(
                new TracingOptions().setFactory(options -> new VertxTracer<Object, Object>() {})));
  }

  @Override
  protected void closeVertx(Vertx vertx) throws Exception {
    vertx.close().toCompletionStage().toCompletableFuture().get(10, SECONDS);
  }
}
