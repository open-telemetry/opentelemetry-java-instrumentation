/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.dbinfo;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class DbInfo {

  public static final DbInfo DEFAULT = builder().build();

  public static DbInfo.Builder builder() {
    return new AutoValue_DbInfo.Builder();
  }

  /** The db.system.name value (e.g., "h2database", "microsoft.sql_server"). */
  @Nullable
  public abstract String getDbSystemName();

  @Nullable
  public abstract String getDbNamespace();

  /**
   * The logical configured target for stable telemetry, or {@code null} when unavailable. This is
   * not the endpoint that handled an individual query.
   */
  @Nullable
  public abstract DbServerTarget getConfiguredServerTarget();

  public Builder toBuilder() {
    return builder()
        .dbSystemName(getDbSystemName())
        .dbNamespace(getDbNamespace())
        .configuredServerTarget(getConfiguredServerTarget());
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder dbSystemName(String dbSystemName);

    public abstract Builder dbNamespace(String dbNamespace);

    public abstract Builder configuredServerTarget(@Nullable DbServerTarget configuredServerTarget);

    public abstract DbInfo build();
  }
}
