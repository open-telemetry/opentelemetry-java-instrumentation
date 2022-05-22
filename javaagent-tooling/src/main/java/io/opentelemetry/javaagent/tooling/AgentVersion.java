/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.javaagent.OpenTelemetryAgent;

public final class AgentVersion {
  public static final String VERSION =
      OpenTelemetryAgent.class.getPackage().getImplementationVersion();

  private AgentVersion() {}
}
