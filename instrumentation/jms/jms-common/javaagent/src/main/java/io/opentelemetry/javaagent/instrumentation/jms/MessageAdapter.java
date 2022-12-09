/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

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
}
