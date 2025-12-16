/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.logging.Level.WARNING;

import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("unused")
final class DeprecatedConfigProperties {

  private static final Logger logger = Logger.getLogger(DeprecatedConfigProperties.class.getName());

  static void warnDeprecatedKeyUsed(String deprecatedPropertyName, String newPropertyName) {
    logger.log(
        WARNING,
        "Deprecated property \"{0}\" was used; use the \"{1}\" property instead",
        new Object[] {deprecatedPropertyName, newPropertyName});
  }

  private DeprecatedConfigProperties() {}
}
