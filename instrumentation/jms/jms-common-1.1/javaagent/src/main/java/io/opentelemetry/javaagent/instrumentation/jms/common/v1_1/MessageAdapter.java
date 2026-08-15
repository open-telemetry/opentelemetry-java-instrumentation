/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

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

  /** Tells whether receive telemetry was already recorded for this message. */
  boolean wasReceiveTelemetryRecorded();

  /** Remembers that receive telemetry was recorded for this message. */
  void markReceiveTelemetryRecorded();
}
