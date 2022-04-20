/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context;

import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.context.ContextKey;
import java.lang.reflect.Field;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class ContextKeyBridge<APPLICATION, AGENT> {

  private final ContextKey<APPLICATION> applicationContextKey;
  private final io.opentelemetry.context.ContextKey<AGENT> agentContextKey;
  private final Function<APPLICATION, AGENT> toAgent;
  private final Function<AGENT, APPLICATION> toApplication;

  public ContextKeyBridge(
      String applicationKeyHolderClassName,
      String agentKeyHolderClassName,
      Function<AGENT, APPLICATION> toApplication,
      Function<APPLICATION, AGENT> toAgent)
      throws Throwable {
    this(applicationKeyHolderClassName, agentKeyHolderClassName, "KEY", toApplication, toAgent);
  }

  public ContextKeyBridge(
      String applicationKeyHolderClassName,
      String agentKeyHolderClassName,
      String fieldName,
      Function<AGENT, APPLICATION> toApplication,
      Function<APPLICATION, AGENT> toAgent)
      throws Throwable {
    this(
        applicationKeyHolderClassName,
        agentKeyHolderClassName,
        fieldName,
        fieldName,
        toApplication,
        toAgent);
  }

  ContextKeyBridge(
      String applicationKeyHolderClassName,
      String agentKeyHolderClassName,
      String applicationFieldName,
      String agentFieldName,
      Function<AGENT, APPLICATION> toApplication,
      Function<APPLICATION, AGENT> toAgent)
      throws Throwable {
    this(
        Class.forName(applicationKeyHolderClassName),
        Class.forName(agentKeyHolderClassName),
        applicationFieldName,
        agentFieldName,
        toApplication,
        toAgent);
  }

  @SuppressWarnings("unchecked")
  public ContextKeyBridge(
      Class<?> applicationKeyHolderClass,
      Class<?> agentKeyHolderClass,
      String applicationFieldName,
      String agentFieldName,
      Function<AGENT, APPLICATION> toApplication,
      Function<APPLICATION, AGENT> toAgent)
      throws Throwable {

    Field applicationContextKeyField =
        applicationKeyHolderClass.getDeclaredField(applicationFieldName);
    applicationContextKeyField.setAccessible(true);
    this.applicationContextKey = (ContextKey<APPLICATION>) applicationContextKeyField.get(null);

    Field agentContextKeyField = agentKeyHolderClass.getDeclaredField(agentFieldName);
    agentContextKeyField.setAccessible(true);
    this.agentContextKey =
        (io.opentelemetry.context.ContextKey<AGENT>) agentContextKeyField.get(null);

    this.toApplication = toApplication;
    this.toAgent = toAgent;
  }

  @Nullable
  <V> V get(AgentContextWrapper contextWrapper, ContextKey<V> requestedKey) {
    if (requestedKey == applicationContextKey) {
      AGENT agentValue = contextWrapper.agentContext.get(agentContextKey);
      if (agentValue == null) {
        return null;
      }
      APPLICATION applicationValue = toApplication.apply(agentValue);
      @SuppressWarnings("unchecked")
      V castValue = (V) applicationValue;
      return castValue;
    }
    return null;
  }

  @Nullable
  <V> Context with(AgentContextWrapper contextWrapper, ContextKey<V> requestedKey, V value) {
    if (requestedKey == applicationContextKey) {
      @SuppressWarnings("unchecked")
      APPLICATION applicationValue = (APPLICATION) value;
      AGENT agentValue = toAgent.apply(applicationValue);
      if (agentValue == null) {
        return contextWrapper;
      }
      return new AgentContextWrapper(
          contextWrapper.agentContext.with(agentContextKey, agentValue),
          contextWrapper.applicationContext);
    }
    return null;
  }
}
