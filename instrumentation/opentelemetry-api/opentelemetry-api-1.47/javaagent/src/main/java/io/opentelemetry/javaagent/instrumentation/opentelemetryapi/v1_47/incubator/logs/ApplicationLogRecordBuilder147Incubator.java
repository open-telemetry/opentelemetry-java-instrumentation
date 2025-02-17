/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_47.incubator.logs;

import application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_42.logs.ApplicationLogRecordBuilder142;

public class ApplicationLogRecordBuilder147Incubator extends ApplicationLogRecordBuilder142
    implements ExtendedLogRecordBuilder {

  private final io.opentelemetry.api.logs.LogRecordBuilder agentLogRecordBuilder;

  ApplicationLogRecordBuilder147Incubator(LogRecordBuilder agentLogRecordBuilder) {
    super(agentLogRecordBuilder);
    this.agentLogRecordBuilder = agentLogRecordBuilder;
  }

  @Override
  public ExtendedLogRecordBuilder setEventName(String eventName) {
    ((io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder) agentLogRecordBuilder)
        .setEventName(eventName);
    return this;
  }
}
