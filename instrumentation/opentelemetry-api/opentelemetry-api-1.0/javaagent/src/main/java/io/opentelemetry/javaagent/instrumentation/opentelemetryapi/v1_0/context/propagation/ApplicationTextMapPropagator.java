/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.propagation;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Iterator;
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

    @Nullable private static final MethodHandle GET_ALL = findGetAll();

    @Nullable
    private static MethodHandle findGetAll() {
      try {
        // getAll is available on the application API starting with 1.50.
        return MethodHandles.publicLookup()
            .findVirtual(
                application.io.opentelemetry.context.propagation.TextMapGetter.class,
                "getAll",
                MethodType.methodType(Iterator.class, Object.class, String.class));
      } catch (NoSuchMethodException ignored) {
        return null;
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Failed to access application TextMapGetter.getAll", e);
      }
    }

    private final application.io.opentelemetry.context.propagation.TextMapGetter<C>
        applicationGetter;

    AgentGetter(
        application.io.opentelemetry.context.propagation.TextMapGetter<C> applicationGetter) {
      this.applicationGetter = applicationGetter;
    }

    @SuppressWarnings("deprecation") // retain keys for older application APIs
    @Override
    public Iterable<String> keys(C c) {
      return applicationGetter.keys(c);
    }

    @Override
    @Nullable
    public String get(@Nullable C carrier, String key) {
      return applicationGetter.get(carrier, key);
    }

    @Override
    public Iterator<String> getAll(@Nullable C carrier, String key) {
      if (GET_ALL == null) {
        return TextMapGetter.super.getAll(carrier, key);
      }
      try {
        @SuppressWarnings("unchecked") // TextMapGetter.getAll returns Iterator<String>
        Iterator<String> values =
            (Iterator<String>) GET_ALL.invoke(applicationGetter, carrier, key);
        return values;
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable t) {
        throw new IllegalStateException("Failed to invoke application TextMapGetter.getAll", t);
      }
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
      applicationSetter.set(carrier, key, value);
    }
  }
}
