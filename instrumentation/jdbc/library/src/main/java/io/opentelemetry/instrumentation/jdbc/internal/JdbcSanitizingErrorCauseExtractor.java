/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import java.sql.SQLException;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class JdbcSanitizingErrorCauseExtractor implements ErrorCauseExtractor {

  static final JdbcSanitizingErrorCauseExtractor INSTANCE = new JdbcSanitizingErrorCauseExtractor();

  private JdbcSanitizingErrorCauseExtractor() {}

  @Override
  public Throwable extract(Throwable error) {
    Throwable cause = ErrorCauseExtractor.getDefault().extract(error);
    if (cause instanceof SQLException) {
      SQLException original = (SQLException) cause;
      String sanitizedMsg =
          "SQL error [" + original.getErrorCode() + "/" + original.getSQLState() + "]";
      SQLException wrapper =
          new SQLException(sanitizedMsg, original.getSQLState(), original.getErrorCode());
      wrapper.setStackTrace(original.getStackTrace());
      return wrapper;
    }
    return cause;
  }
}
