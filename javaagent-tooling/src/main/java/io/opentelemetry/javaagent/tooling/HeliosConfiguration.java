/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.logging.Level.WARNING;

import java.util.logging.Logger;

public class HeliosConfiguration {

  private static final Logger logger = Logger.getLogger(HeliosConfiguration.class.getName());
  public static final String HELIOS_TEST_TRIGGERED_TRACE = "hs-triggered-test";
  public static final String HELIOS_ENVIRONMENT_ENV_VAR = "HS_ENVIRONMENT";

  public static final String HELIOS_SERVICE_NAME_ENV_VAR = "HS_SERVICE_NAME";
  public static final String HELIOS_TOKEN_ENV_VAR = "HS_TOKEN";

  public static String getEnvironmentName() {
    return System.getenv(HELIOS_ENVIRONMENT_ENV_VAR);
  }

  public static String getServiceName() {
    String serviceName = System.getenv(HELIOS_SERVICE_NAME_ENV_VAR);
    if (serviceName == null) {
      logger.log(WARNING, "service name is mandatory and wasn't defined");
    }
    return serviceName;
  }

  public static String getHsToken() {
    return System.getenv(HELIOS_TOKEN_ENV_VAR);
  }
}
