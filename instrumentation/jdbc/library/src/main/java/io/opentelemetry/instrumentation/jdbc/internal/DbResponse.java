/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class DbResponse {

  public static DbResponse create(@Nullable Long returnedRows) {
    return new AutoValue_DbResponse(returnedRows);
  }

  @Nullable
  public abstract Long getReturnedRows();
}
