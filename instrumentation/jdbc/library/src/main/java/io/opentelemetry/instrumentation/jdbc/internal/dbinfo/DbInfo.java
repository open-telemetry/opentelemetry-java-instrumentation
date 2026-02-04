/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.dbinfo;

import com.google.auto.value.AutoValue;
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

  /** The stable/new db.system.name value (e.g., "h2database", "microsoft.sql_server"). */
  @Nullable
  public abstract String getDbSystemName();

  // @Deprecated to be removed in 3.0
  // Have to leave @Deprecated commented out because AutoValue generates equals()/hashCode() that
  // call this method
  // without @SuppressWarnings, causing build failure with -Werror.
  // @AutoValue.CopyAnnotations does
  // not help because @SuppressWarnings has SOURCE retention and is not copied to generated code.
  @Nullable
  public abstract String getDbSystem();

  // "type:[subtype:]//host:port"
  @Nullable
  public abstract String getDbConnectionString();

  @Nullable
  public abstract String getDbUser();

  @Nullable
  public abstract String getDbName();

  @Nullable
  public abstract String getDbNamespace();

  @Nullable
  public abstract String getServerAddress();

  @Nullable
  public abstract Integer getServerPort();

  public Builder toBuilder() {
    return builder()
        .dbSystemName(getDbSystemName())
        .dbSystem(getDbSystem())
        .dbConnectionString(getDbConnectionString())
        .dbUser(getDbUser())
        .dbName(getDbName())
        .dbNamespace(getDbNamespace())
        .serverAddress(getServerAddress())
        .serverPort(getServerPort());
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder dbSystemName(String dbSystemName);

    // Deprecated: use dbSystemName() instead; to be removed in 3.0.
    // Not using @Deprecated because AutoValue generates equals()/hashCode() that call this method
    // without @SuppressWarnings, causing build failure with -Werror. @AutoValue.CopyAnnotations
    // does not help because @SuppressWarnings has SOURCE retention and is not copied to generated
    // code.
    public abstract Builder dbSystem(String dbSystem);

    public abstract Builder dbConnectionString(String dbConnectionString);

    public abstract Builder dbUser(String dbUser);

    public abstract Builder dbName(String dbName);

    public abstract Builder dbNamespace(String dbNamespace);

    public abstract Builder serverAddress(String serverAddress);

    public abstract Builder serverPort(Integer serverPort);

    public abstract DbInfo build();
  }
}
