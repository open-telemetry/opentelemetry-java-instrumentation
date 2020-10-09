/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context;

import application.io.opentelemetry.context.Scope;

public class ApplicationScope implements Scope {

  private final io.opentelemetry.context.Scope agentScope;

  public ApplicationScope(io.opentelemetry.context.Scope agentScope) {
    this.agentScope = agentScope;
  }

  @Override
  public void close() {
    agentScope.close();
  }
}
