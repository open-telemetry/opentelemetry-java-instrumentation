/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

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

  /** Clears stale receive state before recording a new delivery of this physical message. */
  void prepareForReceive();

  /** Attaches the context created for the receive operation to this message. */
  void setReceiveContext(JmsReceiveContext context);

  /** Returns the context created for this message's receive operation, if there was one. */
  @Nullable
  JmsReceiveContext getReceiveContext();

  /** Starts a possibly nested processing callback for this message. */
  void beginProcessing();

  /** Ends a processing callback for this message. */
  void endProcessing();

  /** Claims responsibility for recording the consumed messages metric for this delivery. */
  boolean claimConsumedMessages();
}
