/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static java.util.Collections.singletonList;

import com.rabbitmq.client.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.amqp.core.Message;

public class SpringRabbitRequest {

  private static final VirtualField<Message, Context> PROCESSING_CONTEXT =
      VirtualField.find(Message.class, Context.class);

  private final Channel channel;
  private final List<Message> messages;
  private final int batchMessageCount;
  @Nullable private ProcessingContextInstallation processingContextInstallation;

  public SpringRabbitRequest(Channel channel, Message message) {
    this.channel = channel;
    this.messages = singletonList(message);
    this.batchMessageCount = 0;
  }

  public SpringRabbitRequest(Channel channel, List<Message> messages) {
    this.channel = channel;
    this.messages = messages;
    this.batchMessageCount = messages.size();
  }

  Channel getChannel() {
    return channel;
  }

  Message getMessage() {
    return messages.get(0);
  }

  List<Message> getMessages() {
    return messages;
  }

  boolean isBatch() {
    return batchMessageCount > 0;
  }

  int getBatchMessageCount() {
    return batchMessageCount;
  }

  void installProcessingContext(Context context) {
    ProcessingContextInstallation installation = null;
    for (Message message : messages) {
      installation =
          new ProcessingContextInstallation(message, PROCESSING_CONTEXT.get(message), installation);
      PROCESSING_CONTEXT.set(message, context);
    }
    processingContextInstallation = installation;
  }

  void restoreProcessingContext(Context context) {
    ProcessingContextInstallation installation = processingContextInstallation;
    processingContextInstallation = null;
    restoreProcessingContext(context, installation);
  }

  private static void restoreProcessingContext(
      Context context, @Nullable ProcessingContextInstallation installation) {
    while (installation != null) {
      if (PROCESSING_CONTEXT.get(installation.message) == context) {
        PROCESSING_CONTEXT.set(installation.message, installation.previousContext);
      }
      installation = installation.previous;
    }
  }

  private static final class ProcessingContextInstallation {
    private final Message message;
    @Nullable private final Context previousContext;
    @Nullable private final ProcessingContextInstallation previous;

    private ProcessingContextInstallation(
        Message message,
        @Nullable Context previousContext,
        @Nullable ProcessingContextInstallation previous) {
      this.message = message;
      this.previousContext = previousContext;
      this.previous = previous;
    }
  }
}
