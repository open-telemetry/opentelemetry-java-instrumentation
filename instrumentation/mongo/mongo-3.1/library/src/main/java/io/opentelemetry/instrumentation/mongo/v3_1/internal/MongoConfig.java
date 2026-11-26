/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MongoConfig {
  public static boolean isQueryTextCaptureEnabled(OpenTelemetry openTelemetry) {
    return DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "mongo")
        .getBoolean("capture_query_text", false);
  }

  private MongoConfig() {}
}
