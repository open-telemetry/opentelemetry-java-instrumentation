/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import io.opentelemetry.instrumentation.r2dbc.v1_0.R2dbcTelemetryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<R2dbcTelemetryBuilder, Boolean> setEnableSqlCommenter;

  /**
   * Sets whether to augment sql query with comment containing the tracing information. See <a
   * href="https://google.github.io/sqlcommenter/">sqlcommenter</a> for more info.
   *
   * <p>WARNING: augmenting queries with tracing context will make query texts unique, which may
   * have adverse impact on database performance. Consult with database experts before enabling.
   */
  public static void setEnableSqlCommenter(
      R2dbcTelemetryBuilder builder, boolean sqlCommenterEnabled) {
    if (setEnableSqlCommenter != null) {
      setEnableSqlCommenter.accept(builder, sqlCommenterEnabled);
    }
  }

  public static void internalSetEnableSqlCommenter(
      BiConsumer<R2dbcTelemetryBuilder, Boolean> setEnableSqlCommenter) {
    Experimental.setEnableSqlCommenter = setEnableSqlCommenter;
  }

  private Experimental() {}
}
