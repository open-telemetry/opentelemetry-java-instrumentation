/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.instrumentation.api.config.Config;
import java.util.logging.Logger;

public final class DeprecatedConfigPropertyWarning {

  private static final Logger logger =
      Logger.getLogger(DeprecatedConfigPropertyWarning.class.getName());

  public static void warnIfUsed(
      Config config, String deprecatedPropertyName, String newPropertyName) {
    if (config.getString(deprecatedPropertyName) != null) {
      logger.log(
          WARNING,
          "Deprecated property '{0}' was used; use the '{1}' property instead",
          new Object[] {deprecatedPropertyName, newPropertyName});
    }
  }

  private DeprecatedConfigPropertyWarning() {}
}
