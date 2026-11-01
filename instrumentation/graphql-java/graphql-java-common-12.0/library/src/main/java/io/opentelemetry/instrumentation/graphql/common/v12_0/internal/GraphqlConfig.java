/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GraphqlConfig {

  public static boolean getOperationNameInSpanNameEnabled(DeclarativeConfigProperties config) {
    return config.get("operation_name_in_span_name").getBoolean("enabled", false);
  }

  public static boolean getQuerySanitizationEnabled(DeclarativeConfigProperties config) {
    return config.get("query_sanitization").getBoolean("enabled", true);
  }

  private GraphqlConfig() {}
}
