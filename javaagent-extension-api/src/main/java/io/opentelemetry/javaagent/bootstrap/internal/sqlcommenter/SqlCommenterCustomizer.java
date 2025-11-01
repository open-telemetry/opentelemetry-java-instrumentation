/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal.sqlcommenter;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenterBuilder;

/**
 * Customize configuration for {@link SqlCommenterBuilder}. This lets extensions configure different
 * propagators and decide whether to prepend or append the sqlcommenter comment based on the
 * database where the query is executed.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface SqlCommenterCustomizer {

  /** Customize the given {@link SqlCommenterBuilder}. */
  void customize(SqlCommenterBuilder builder);
}
