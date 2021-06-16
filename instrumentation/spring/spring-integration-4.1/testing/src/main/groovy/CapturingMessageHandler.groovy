/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import java.util.concurrent.CompletableFuture
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHandler
import org.springframework.messaging.MessagingException

class CapturingMessageHandler implements MessageHandler {
  final CompletableFuture<Message<?>> captured = new CompletableFuture<>()

  @Override
  void handleMessage(Message<?> message) throws MessagingException {
    runUnderTrace("handler") {
      captured.complete(message)
    }
  }

  Message<?> join() {
    captured.join()
  }
}
