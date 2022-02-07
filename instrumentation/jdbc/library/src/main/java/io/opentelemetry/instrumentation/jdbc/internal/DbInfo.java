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
public abstract class DbInfo {

  public static final DbInfo DEFAULT = builder().build();

  public static DbInfo.Builder builder() {
    return new AutoValue_DbInfo.Builder();
  }

  @Nullable
  public abstract String getSystem();

  @Nullable
  public abstract String getSubtype();

  // "type:[subtype:]//host:port"
  @Nullable
  public abstract String getShortUrl();

  @Nullable
  public abstract String getUser();

  @Nullable
  public abstract String getName();

  @Nullable
  public abstract String getDb();

  @Nullable
  public abstract String getHost();

  @Nullable
  public abstract Integer getPort();

  public Builder toBuilder() {
    return builder()
        .system(getSystem())
        .subtype(getSubtype())
        .shortUrl(getShortUrl())
        .user(getUser())
        .name(getName())
        .db(getDb())
        .host(getHost())
        .port(getPort());
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder system(String system);

    public abstract Builder subtype(String subtype);

    public abstract Builder shortUrl(String shortUrl);

    public abstract Builder user(String user);

    public abstract Builder name(String name);

    public abstract Builder db(String db);

    public abstract Builder host(String host);

    public abstract Builder port(Integer port);

    public abstract DbInfo build();
  }
}
