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
      ApplicationEnvironmentPreparedEvent event,
      String deprecatedPropertyName,
      String newPropertyName) {
    warnIfUsed(event, deprecatedPropertyName, newPropertyName);
    Boolean value = event.getEnvironment().getProperty(deprecatedPropertyName, Boolean.class);
    if (value != null) {
      return value;
    }
    return event.getEnvironment().getProperty(newPropertyName, Boolean.class);
  }

  private static void warnIfUsed(
      ApplicationEnvironmentPreparedEvent event,
      String deprecatedPropertyName,
      String newPropertyName) {
    String value = event.getEnvironment().getProperty(deprecatedPropertyName, String.class);
    if (value != null) {
      logger.log(
          WARNING,
          "Deprecated property \"{0}\" was used; use the \"{1}\" property instead",
          new Object[] {deprecatedPropertyName, newPropertyName});
    }
  }

  private DeprecatedConfigProperties() {}
}
