/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.logs;

import application.io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_42.logs.ApplicationLogRecordBuilder142;

public class ApplicationLogRecordBuilder150 extends ApplicationLogRecordBuilder142
    implements LogRecordBuilder {

  private final io.opentelemetry.api.logs.LogRecordBuilder agentLogRecordBuilder;

  protected ApplicationLogRecordBuilder150(
      io.opentelemetry.api.logs.LogRecordBuilder agentLogRecordBuilder) {
    super(agentLogRecordBuilder);
    this.agentLogRecordBuilder = agentLogRecordBuilder;
  }

  @Override
  public LogRecordBuilder setEventName(String eventName) {
    agentLogRecordBuilder.setEventName(eventName);
    return this;
  }
}
