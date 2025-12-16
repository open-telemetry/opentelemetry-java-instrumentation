/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.logging.Level.WARNING;

import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("unused")
final class DeprecatedConfigProperties {

  private static final Logger logger = Logger.getLogger(DeprecatedConfigProperties.class.getName());

  @Nullable
  static String warnIfUsed(
      String deprecatedPropertyName,
      @Nullable String deprecatedPropertyValue,
      String newPropertyName,
      @Nullable String newPropertyValue) {
    if (newPropertyValue != null) {
      return newPropertyValue;
    }

    if (deprecatedPropertyValue == null) {
      return null;
    }

    logger.log(
        WARNING,
        "Deprecated property \"{0}\" was used; use the \"{1}\" property instead",
        new Object[] {deprecatedPropertyName, newPropertyName});
    return deprecatedPropertyValue;
  }

  private DeprecatedConfigProperties() {}
}
