/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageProducer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@SuppressWarnings("deprecation") // send/reply with a reply handler are deprecated in later 3.x
class Vertx3EventBusTest extends AbstractVertxEventBusTest {

  @Override
  protected Vertx createVertx() throws Exception {
    return Vertx.vertx();
  }

  @Override
  protected void closeVertx(Vertx vertx) throws Exception {
    CompletableFuture<Void> closed = new CompletableFuture<>();
    vertx.close(ar -> closed.complete(null));
    closed.get(10, SECONDS);
  }

  @Override
  protected void request(
      EventBus eventBus, String address, Object body, Consumer<Message<Object>> onReply) {
    eventBus.send(address, body, (AsyncResult<Message<Object>> ar) -> onReply.accept(ar.result()));
  }

  @Override
  protected void write(MessageProducer<Object> producer, Object body) {
    producer.write(body);
  }

  @Override
  protected void replyAndRequest(Message<?> message, Object body, Runnable onReply) {
    message.reply(body, (AsyncResult<Message<Object>> ar) -> onReply.run());
  }
}
