/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

final class TestHandler extends Handler {
  private final List<LogRecord> records = new ArrayList<>();

  @Override
  public void publish(LogRecord record) {
    records.add(record);
  }

  @Override
  public void flush() {}

  @Override
  public void close() {
    records.clear();
  }

  List<LogRecord> getRecords() {
    return records;
  }
}
