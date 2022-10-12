/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.logs.bridge;

import application.io.opentelemetry.api.logs.Severity;
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
// Our convention for accessing agent package
@SuppressWarnings("UnnecessarilyFullyQualified")
public class LogBridging {

  private static final EnumMap<Severity, io.opentelemetry.api.logs.Severity> severityMap;

  static {
    severityMap = new EnumMap<>(Severity.class);
    for (Severity severity : Severity.values()) {
      try {
        severityMap.put(severity, io.opentelemetry.api.logs.Severity.valueOf(severity.name()));
      } catch (IllegalArgumentException e) {
        // No mapping exists for this severity, ignore
      }
    }
  }

  public static io.opentelemetry.api.logs.Severity toAgent(Severity applicationSeverity) {
    return severityMap.getOrDefault(
        applicationSeverity, io.opentelemetry.api.logs.Severity.UNDEFINED_SEVERITY_NUMBER);
  }

  private LogBridging() {}
}
