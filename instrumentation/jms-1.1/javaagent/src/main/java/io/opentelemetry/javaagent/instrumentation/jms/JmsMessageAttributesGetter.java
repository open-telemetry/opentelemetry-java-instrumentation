/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.jms.JMSException;

enum JmsMessageAttributesGetter implements MessagingAttributesGetter<MessageWithDestination, Void> {
  INSTANCE;

  private static final Logger logger = Logger.getLogger(JmsMessageAttributesGetter.class.getName());

  @Override
  public String system(MessageWithDestination messageWithDestination) {
    return "jms";
  }

  @Nullable
  @Override
  public String destinationKind(MessageWithDestination messageWithDestination) {
    return messageWithDestination.destinationKind();
  }

  @Nullable
  @Override
  public String destination(MessageWithDestination messageWithDestination) {
    return messageWithDestination.destinationName();
  }

  @Override
  public boolean temporaryDestination(MessageWithDestination messageWithDestination) {
    return messageWithDestination.isTemporaryDestination();
  }

  @Nullable
  @Override
  public String protocol(MessageWithDestination messageWithDestination) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(MessageWithDestination messageWithDestination) {
    return null;
  }

  @Nullable
  @Override
  public String url(MessageWithDestination messageWithDestination) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(MessageWithDestination messageWithDestination) {
    try {
      return messageWithDestination.message().getJMSCorrelationID();
    } catch (JMSException e) {
      logger.log(FINE, "Failure getting JMS correlation id", e);
      return null;
    }
  }

  @Nullable
  @Override
  public Long messagePayloadSize(MessageWithDestination messageWithDestination) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(MessageWithDestination messageWithDestination) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(MessageWithDestination messageWithDestination, Void unused) {
    try {
      return messageWithDestination.message().getJMSMessageID();
    } catch (JMSException e) {
      logger.log(FINE, "Failure getting JMS message id", e);
      return null;
    }
  }
}
