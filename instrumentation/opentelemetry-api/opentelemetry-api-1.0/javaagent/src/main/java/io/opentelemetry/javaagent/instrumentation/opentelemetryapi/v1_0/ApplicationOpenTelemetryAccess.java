/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0;

import static java.util.logging.Level.WARNING;

import java.util.logging.Logger;

public final class ApplicationOpenTelemetryAccess {

  private ApplicationOpenTelemetryAccess() {}

  public static application.io.opentelemetry.api.OpenTelemetry getInstanceOrNoop() {
    try {
      return ApplicationOpenTelemetry.INSTANCE;
    } catch (LinkageError e) {
      Logger.getLogger(ApplicationOpenTelemetryAccess.class.getName())
          .log(
              WARNING,
              "Failed to install the OpenTelemetry API bridge; GlobalOpenTelemetry will act as"
                  + " a no-op for the remainder of this JVM's lifetime.",
              e);
      return application.io.opentelemetry.api.OpenTelemetry.noop();
    }
  }
}
