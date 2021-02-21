/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation;

import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.context.propagation.TextMapGetter;
import application.io.opentelemetry.context.propagation.TextMapPropagator;
import application.io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import java.util.Collection;

class ApplicationTextMapPropagator implements TextMapPropagator {

  private final io.opentelemetry.context.propagation.TextMapPropagator agentTextMapPropagator;

  ApplicationTextMapPropagator(
      io.opentelemetry.context.propagation.TextMapPropagator agentTextMapPropagator) {
    this.agentTextMapPropagator = agentTextMapPropagator;
  }

  @Override
  public Collection<String> fields() {
    return agentTextMapPropagator.fields();
  }

  @Override
  public <C> Context extract(
      Context applicationContext, C carrier, TextMapGetter<C> applicationGetter) {
    io.opentelemetry.context.Context agentContext =
        AgentContextStorage.getAgentContext(applicationContext);
    io.opentelemetry.context.Context agentUpdatedContext =
        agentTextMapPropagator.extract(agentContext, carrier, new AgentGetter<>(applicationGetter));
    if (agentUpdatedContext == agentContext) {
      return applicationContext;
    }
    return new AgentContextStorage.AgentContextWrapper(agentUpdatedContext, applicationContext);
  }

  @Override
  public <C> void inject(
      Context applicationContext, C carrier, TextMapSetter<C> applicationSetter) {
    io.opentelemetry.context.Context agentContext =
        AgentContextStorage.getAgentContext(applicationContext);
    agentTextMapPropagator.inject(agentContext, carrier, new AgentSetter<>(applicationSetter));
  }

  private static class AgentGetter<C>
      implements io.opentelemetry.context.propagation.TextMapGetter<C> {

    private final TextMapGetter<C> applicationGetter;

    AgentGetter(TextMapGetter<C> applicationGetter) {
      this.applicationGetter = applicationGetter;
    }

    @Override
    public Iterable<String> keys(C c) {
      return applicationGetter.keys(c);
    }

    @Override
    public String get(C carrier, String key) {
      return applicationGetter.get(carrier, key);
    }
  }

  private static class AgentSetter<C>
      implements io.opentelemetry.context.propagation.TextMapSetter<C> {

    private final TextMapSetter<C> applicationSetter;

    AgentSetter(TextMapSetter<C> applicationSetter) {
      this.applicationSetter = applicationSetter;
    }

    @Override
    public void set(C carrier, String key, String value) {
      applicationSetter.set(carrier, key, value);
    }
  }
}
