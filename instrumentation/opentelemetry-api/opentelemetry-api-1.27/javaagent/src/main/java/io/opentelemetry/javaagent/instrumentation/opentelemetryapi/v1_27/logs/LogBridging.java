/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import io.opentelemetry.api.logs.Severity;
import java.util.EnumMap;

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
public class LogBridging {

  private static final EnumMap<application.io.opentelemetry.api.logs.Severity, Severity>
      severityMap;

  static {
    severityMap = new EnumMap<>(application.io.opentelemetry.api.logs.Severity.class);
    for (application.io.opentelemetry.api.logs.Severity severity :
        application.io.opentelemetry.api.logs.Severity.values()) {
      try {
        severityMap.put(severity, Severity.valueOf(severity.name()));
      } catch (IllegalArgumentException e) {
        // No mapping exists for this severity, ignore
      }
    }
  }

  public static Severity toAgent(
      application.io.opentelemetry.api.logs.Severity applicationSeverity) {
    return severityMap.getOrDefault(applicationSeverity, Severity.UNDEFINED_SEVERITY_NUMBER);
  }

  private LogBridging() {}
}
