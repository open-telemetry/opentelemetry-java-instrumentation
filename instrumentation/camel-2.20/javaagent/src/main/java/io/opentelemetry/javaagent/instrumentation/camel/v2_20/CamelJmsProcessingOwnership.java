/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageProcessingState;
import javax.jms.Message;
import javax.jms.MessageListener;

public final class CamelJmsProcessingOwnership {

  public static final VirtualField<Message, JmsMessageProcessingState> PROCESSING_STATE =
      VirtualField.find(Message.class, JmsMessageProcessingState.class);

  private static final VirtualField<MessageListener, Boolean> CAMEL_OWNS_PROCESSING =
      VirtualField.find(MessageListener.class, Boolean.class);

  public static void markCamelAsProcessingOwner(MessageListener messageListener) {
    CAMEL_OWNS_PROCESSING.set(messageListener, true);
  }

  private CamelJmsProcessingOwnership() {}
}
