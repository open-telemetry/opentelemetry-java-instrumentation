/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_63.logs;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.logs.ApplicationLogRecordBuilder150;

public class ApplicationLogRecordBuilder163 extends ApplicationLogRecordBuilder150
    implements application.io.opentelemetry.api.logs.LogRecordBuilder {

  private final LogRecordBuilder agentLogRecordBuilder;

  protected ApplicationLogRecordBuilder163(LogRecordBuilder agentLogRecordBuilder) {
    super(agentLogRecordBuilder);
    this.agentLogRecordBuilder = agentLogRecordBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LogRecordBuilder setException(Throwable throwable) {
    agentLogRecordBuilder.setException(throwable);
    return this;
  }
}
