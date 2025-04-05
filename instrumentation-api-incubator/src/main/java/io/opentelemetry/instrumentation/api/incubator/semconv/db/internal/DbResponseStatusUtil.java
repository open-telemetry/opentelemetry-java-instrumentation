/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class DbResponseStatusUtil {
  private DbResponseStatusUtil() {}

  @Nullable
  public static String dbResponseStatusCode(int responseStatusCode) {
    return isError(responseStatusCode) ? Integer.toString(responseStatusCode) : null;
  }

  private static boolean isError(int responseStatusCode) {
    return responseStatusCode >= 400
        ||
        // invalid status code, does not exist
        responseStatusCode < 100;
  }
}
