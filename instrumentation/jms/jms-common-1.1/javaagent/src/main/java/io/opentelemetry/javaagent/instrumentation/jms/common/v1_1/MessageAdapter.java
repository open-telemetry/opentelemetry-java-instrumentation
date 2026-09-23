/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageProcessingState;
import io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveContext;
import java.util.List;
import javax.annotation.Nullable;

public interface MessageAdapter {

  @Nullable
  DestinationAdapter getJmsDestination() throws Exception;

  List<String> getPropertyNames() throws Exception;

  @Nullable
  Object getObjectProperty(String key) throws Exception;

  @Nullable
  String getStringProperty(String key) throws Exception;

  void setStringProperty(String key, String value) throws Exception;

  @Nullable
  String getJmsCorrelationId() throws Exception;

  @Nullable
  String getJmsMessageId() throws Exception;

  /** Starts a new delivery for a message returned by a receive operation. */
  JmsMessageProcessingState prepareForReceive();

  /** Attaches the context created for the receive operation to this message. */
  void setReceiveContext(JmsReceiveContext context);

  /** Returns the context created for this message's receive operation, if there was one. */
  @Nullable
  JmsReceiveContext getReceiveContext();

  /** Starts processing and returns whether this is the first observer for this delivery. */
  boolean beginProcessing();

  /** Ends a processing callback for this message. */
  void endProcessing();

  /** Ends processing when listener setup fails without replacing the setup failure. */
  default void endProcessingAfterStartFailure(Throwable startFailure) {
    try {
      endProcessing();
    } catch (Throwable cleanupFailure) {
      if (cleanupFailure != startFailure) {
        try {
          startFailure.addSuppressed(cleanupFailure);
        } catch (Throwable ignored) {
          // Keep the setup failure as the throwable suppressed by the advice.
        }
      }
    }
  }
}
