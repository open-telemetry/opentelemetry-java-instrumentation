/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class TransactionRequest {

  public static TransactionRequest create(DbInfo dbInfo, String operation) {
    return new AutoValue_TransactionRequest(dbInfo, operation);
  }

  public abstract DbInfo getDbInfo();

  public abstract String operation();

  public static String spanName(TransactionRequest request) {
    return request.operation();
  }
}
