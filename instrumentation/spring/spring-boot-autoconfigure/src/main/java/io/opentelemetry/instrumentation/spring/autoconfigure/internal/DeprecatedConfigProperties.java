/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static java.util.logging.Level.WARNING;

import java.util.logging.Logger;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("unused") // keep around for next time even if not currently used
public final class DeprecatedConfigProperties {

  private static final Logger logger = Logger.getLogger(DeprecatedConfigProperties.class.getName());

  public static Boolean getBoolean(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent,
      String deprecatedPropertyName,
      String newPropertyName) {
    warnIfUsed(applicationEnvironmentPreparedEvent, deprecatedPropertyName, newPropertyName);
    Boolean deprecatedValue =
        applicationEnvironmentPreparedEvent
            .getEnvironment()
            .getProperty(deprecatedPropertyName, Boolean.class);
    Boolean newValue =
        applicationEnvironmentPreparedEvent
            .getEnvironment()
            .getProperty(newPropertyName, Boolean.class);

    // Return the new value if it exists, otherwise return the deprecated value
    return newValue != null ? newValue : deprecatedValue;
  }

  private static void warnIfUsed(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent,
      String deprecatedPropertyName,
      String newPropertyName) {
    if (applicationEnvironmentPreparedEvent.getEnvironment().getProperty(deprecatedPropertyName)
        != null) {
      logger.log(
          WARNING,
          "Deprecated property \"{0}\" was used; use the \"{1}\" property instead",
          new Object[] {deprecatedPropertyName, newPropertyName});
    }
  }

  private DeprecatedConfigProperties() {}
}
