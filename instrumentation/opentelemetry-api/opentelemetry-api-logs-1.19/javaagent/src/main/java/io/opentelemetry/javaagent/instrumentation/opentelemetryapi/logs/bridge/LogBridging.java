/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.logs.bridge;

import static java.util.logging.Level.FINE;

import application.io.opentelemetry.api.logs.Severity;
import java.util.logging.Logger;

/**
 * This class translates between the (unshaded) OpenTelemetry API that the application brings and
 * the (shaded) OpenTelemetry API that is used by the agent.
 *
 * <p>"application.io.opentelemetry.*" refers to the (unshaded) OpenTelemetry API that the
 * application brings (as those references will be translated during the build to remove the
 * "application." prefix).
 *
 * <p>"io.opentelemetry.*" refers to the (shaded) OpenTelemetry API that is used by the agent (as
 * those references will later be shaded).
 *
 * <p>Also see comments in this module's gradle file.
 */
// Our convention for accessing agent package
@SuppressWarnings("UnnecessarilyFullyQualified")
public class LogBridging {

  private static final Logger logger = Logger.getLogger(LogBridging.class.getName());

  public static io.opentelemetry.api.logs.Severity toAgent(Severity applicationSeverity) {
    io.opentelemetry.api.logs.Severity agentSeverity;
    try {
      agentSeverity = io.opentelemetry.api.logs.Severity.valueOf(applicationSeverity.name());
    } catch (IllegalArgumentException e) {
      logger.log(FINE, "unexpected status canonical code: {0}", applicationSeverity.name());
      return io.opentelemetry.api.logs.Severity.UNDEFINED_SEVERITY_NUMBER;
    }
    return agentSeverity;
  }

  private LogBridging() {}
}
