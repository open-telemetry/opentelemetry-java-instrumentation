/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageProducer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // send/reply with a reply handler are deprecated in later 3.x
class Vertx39EventBusTest extends AbstractVertxEventBusTest {

  @Override
  protected Vertx createVertx() {
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
    eventBus.request(
        address, body, (AsyncResult<Message<Object>> ar) -> onReply.accept(ar.result()));
  }

  @Override
  protected void write(MessageProducer<Object> producer, Object body) {
    producer.write(body);
  }

  @Override
  protected void replyAndRequest(Message<?> message, Object body, Runnable onReply) {
    message.reply(body, (AsyncResult<Message<Object>> ar) -> onReply.run());
  }

  @Test
  void producerWithReplyHandler() throws InterruptedException {
    String address = "address.producer." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    eventBus.consumer(address, message -> message.reply("pong"));

    CountDownLatch replied = new CountDownLatch(1);
    testing.runWithSpan(
        "parent",
        () ->
            eventBus
                .sender(address)
                .send("ping", (AsyncResult<Message<Object>> ar) -> replied.countDown()));

    assertThat(replied.await(10, SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("process " + address)
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))));
  }
}
