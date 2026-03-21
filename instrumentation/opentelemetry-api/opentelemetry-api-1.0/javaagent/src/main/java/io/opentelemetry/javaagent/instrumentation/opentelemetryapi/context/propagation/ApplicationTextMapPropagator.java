/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import java.util.Collection;
import javax.annotation.Nullable;

class ApplicationTextMapPropagator
    implements application.io.opentelemetry.context.propagation.TextMapPropagator {

  private final TextMapPropagator agentTextMapPropagator;

  ApplicationTextMapPropagator(TextMapPropagator agentTextMapPropagator) {
    this.agentTextMapPropagator = agentTextMapPropagator;
  }

  @Override
  public Collection<String> fields() {
    return agentTextMapPropagator.fields();
  }

  @Override
  public <C> application.io.opentelemetry.context.Context extract(
      application.io.opentelemetry.context.Context applicationContext,
      C carrier,
      application.io.opentelemetry.context.propagation.TextMapGetter<C> applicationGetter) {
    Context agentContext = AgentContextStorage.getAgentContext(applicationContext);
    Context agentUpdatedContext =
        agentTextMapPropagator.extract(agentContext, carrier, new AgentGetter<>(applicationGetter));
    if (agentUpdatedContext == agentContext) {
      return applicationContext;
    }
    return AgentContextStorage.newContextWrapper(agentUpdatedContext, applicationContext);
  }

  @Override
  public <C> void inject(
      application.io.opentelemetry.context.Context applicationContext,
      C carrier,
      application.io.opentelemetry.context.propagation.TextMapSetter<C> applicationSetter) {
    Context agentContext = AgentContextStorage.getAgentContext(applicationContext);
    agentTextMapPropagator.inject(agentContext, carrier, new AgentSetter<>(applicationSetter));
  }

  private static class AgentGetter<C> implements TextMapGetter<C> {

    private final application.io.opentelemetry.context.propagation.TextMapGetter<C>
        applicationGetter;

    AgentGetter(
        application.io.opentelemetry.context.propagation.TextMapGetter<C> applicationGetter) {
      this.applicationGetter = applicationGetter;
    }

    @Override
    public Iterable<String> keys(C c) {
      return applicationGetter.keys(c);
    }

    @Override
    @Nullable
    public String get(@Nullable C carrier, String key) {
      if (carrier == null) {
        return null;
      }
      return applicationGetter.get(carrier, key);
    }
  }

  private static class AgentSetter<C> implements TextMapSetter<C> {

    private final application.io.opentelemetry.context.propagation.TextMapSetter<C>
        applicationSetter;

    AgentSetter(
        application.io.opentelemetry.context.propagation.TextMapSetter<C> applicationSetter) {
      this.applicationSetter = applicationSetter;
    }

    @Override
    public void set(@Nullable C carrier, String key, String value) {
      if (carrier == null) {
        return;
      }
      applicationSetter.set(carrier, key, value);
    }
  }
}
