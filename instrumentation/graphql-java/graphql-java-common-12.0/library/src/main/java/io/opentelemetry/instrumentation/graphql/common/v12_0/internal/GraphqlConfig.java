/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GraphqlConfig {

  private static final Logger logger = Logger.getLogger(GraphqlConfig.class.getName());

  public static boolean getOperationNameInSpanNameEnabled(DeclarativeConfigProperties config) {
    Boolean enabled = config.get("operation_name_in_span_name").getBoolean("enabled");
    if (enabled != null) {
      return enabled;
    }

    // Support the deprecated config key until 3.0.
    if (!SemconvStability.v3Preview()) {
      Boolean deprecatedEnabled =
          config.get("add_operation_name_to_span_name").getBoolean("enabled");
      if (deprecatedEnabled != null) {
        logger.warning(
            "The otel.instrumentation.graphql.add-operation-name-to-span-name.enabled setting is"
                + " deprecated and will be removed in 3.0. Use "
                + "otel.instrumentation.graphql.operation-name-in-span-name.enabled instead.");
        return deprecatedEnabled;
      }
    }

    return false;
  }

  private GraphqlConfig() {}
}
