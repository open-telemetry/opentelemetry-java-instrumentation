/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender;

public enum Severity {
  UNDEFINED_SEVERITY_NUMBER(0),
  TRACE(1),
  TRACE2(2),
  TRACE3(3),
  TRACE4(4),
  DEBUG(5),
  DEBUG2(6),
  DEBUG3(7),
  DEBUG4(8),
  INFO(9),
  INFO2(10),
  INFO3(11),
  INFO4(12),
  WARN(13),
  WARN2(14),
  WARN3(15),
  WARN4(16),
  ERROR(17),
  ERROR2(18),
  ERROR3(19),
  ERROR4(20),
  FATAL(21),
  FATAL2(22),
  FATAL3(23),
  FATAL4(24);

  private final int severityNumber;

  Severity(int severityNumber) {
    this.severityNumber = severityNumber;
  }

  public int getSeverityNumber() {
    return severityNumber;
  }
}
