/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;

import java.util.concurrent.CompletableFuture;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

class CapturingMessageHandler implements MessageHandler {
  final CompletableFuture<Message<?>> captured = new CompletableFuture<>();

  @Override
  public void handleMessage(Message<?> message) {
    runWithSpan(
        "handler",
        () -> {
          captured.complete(message);
        });
  }

  Message<?> join() {
    return captured.join();
  }
}
