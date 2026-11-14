/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageProducer;
import java.util.function.Consumer;

class Vertx4EventBusTest extends AbstractVertxEventBusTest {

  @Override
  protected Vertx createVertx() throws Exception {
    return Vertx.vertx();
  }

  @Override
  protected void closeVertx(Vertx vertx) throws Exception {
    vertx.close().toCompletionStage().toCompletableFuture().get(10, SECONDS);
  }

  @Override
  protected void request(
      EventBus eventBus, String address, Object body, Consumer<Message<Object>> onReply) {
    eventBus.<Object>request(address, body, ar -> onReply.accept(ar.result()));
  }

  @Override
  protected void write(MessageProducer<Object> producer, Object body) {
    producer.write(body);
  }

  @Override
  protected void replyAndRequest(Message<?> message, Object body, Runnable onReply) {
    message.replyAndRequest(body, ar -> onReply.run());
  }
}
