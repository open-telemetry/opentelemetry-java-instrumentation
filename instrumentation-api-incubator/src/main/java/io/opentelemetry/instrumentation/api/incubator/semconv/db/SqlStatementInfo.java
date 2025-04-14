/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import com.google.auto.value.AutoValue;
import java.util.Map;
import javax.annotation.Nullable;

@AutoValue
public abstract class SqlStatementInfo {

  public static SqlStatementInfo create(
      @Nullable String fullStatement,
      @Nullable String operation,
      @Nullable String identifier,
      @Nullable Map<String, String> parameters) {
    return new AutoValue_SqlStatementInfo(fullStatement, operation, identifier, parameters);
  }

  @Nullable
  public abstract String getFullStatement();

  @Nullable
  public abstract String getOperation();

  @Nullable
  public abstract String getMainIdentifier();

  @Nullable
  public abstract Map<String, String> getParameters();
}
