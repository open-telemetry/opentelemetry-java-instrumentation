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

  @Nullable
  public abstract String getSubtype();

  // "type:[subtype:]//host:port"
  @Nullable
  public abstract String getDbConnectionString();

  @Nullable
  public abstract String getDbUser();

  @Nullable
  public abstract String getDbName();

  @Nullable
  public abstract String getDbNamespace();

  /** The parser's single host, including defaults, used for legacy telemetry and pool names. */
  @Nullable
  public abstract String getLegacyServerAddress();

  /** The parser's single port, including defaults, used for legacy telemetry and pool names. */
  @Nullable
  public abstract Integer getLegacyServerPort();

  /**
   * The logical configured target for stable telemetry, or {@code null} when unavailable. This is
   * not the endpoint that handled an individual query.
   */
  @Nullable
  public abstract DbServerTarget getConfiguredServerTarget();

  @Nullable
  public final String getSystem() {
    return getDbSystem() != null ? getDbSystem() : getDbSystemName();
  }

  @Nullable
  public final String getShortUrl() {
    return getDbConnectionString();
  }

  @Nullable
  public final String getUser() {
    return getDbUser();
  }

  @Nullable
  public final String getName() {
    return getDbName() != null ? getDbName() : getDbNamespace();
  }

  @Nullable
  public final String getHost() {
    return getLegacyServerAddress();
  }

  @Nullable
  public final Integer getPort() {
    return getLegacyServerPort();
  }

  public Builder toBuilder() {
    return builder()
        .dbSystemName(getDbSystemName())
        .dbSystem(getDbSystem())
        .subtype(getSubtype())
        .dbConnectionString(getDbConnectionString())
        .dbUser(getDbUser())
        .dbName(getDbName())
        .dbNamespace(getDbNamespace())
        .legacyServerAddress(getLegacyServerAddress())
        .legacyServerPort(getLegacyServerPort())
        .configuredServerTarget(getConfiguredServerTarget());
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

    public abstract Builder subtype(String subtype);

    public abstract Builder dbConnectionString(String dbConnectionString);

    public abstract Builder dbUser(String dbUser);

    public abstract Builder dbName(String dbName);

    public abstract Builder dbNamespace(String dbNamespace);

    public abstract Builder legacyServerAddress(String legacyServerAddress);

    public abstract Builder legacyServerPort(Integer legacyServerPort);

    public abstract Builder configuredServerTarget(@Nullable DbServerTarget configuredServerTarget);

    public final Builder system(String system) {
      return dbSystemName(system).dbSystem(system);
    }

    public final Builder shortUrl(String shortUrl) {
      return dbConnectionString(shortUrl);
    }

    public final Builder user(String user) {
      return dbUser(user);
    }

    public final Builder name(String name) {
      return dbNamespace(name).dbName(name);
    }

    public final Builder host(String host) {
      return legacyServerAddress(host);
    }

    public final Builder port(Integer port) {
      return legacyServerPort(port);
    }

    public abstract DbInfo build();
  }
}
