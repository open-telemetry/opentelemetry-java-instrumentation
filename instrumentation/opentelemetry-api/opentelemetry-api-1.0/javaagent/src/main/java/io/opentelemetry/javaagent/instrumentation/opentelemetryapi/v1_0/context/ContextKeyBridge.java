/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.lang.reflect.Field;
import java.util.function.Function;
import javax.annotation.Nullable;

final class ContextKeyBridge<APPLICATION, AGENT> {

  private final application.io.opentelemetry.context.ContextKey<APPLICATION> applicationContextKey;
  private final ContextKey<AGENT> agentContextKey;
  private final Function<APPLICATION, AGENT> toAgent;
  private final Function<AGENT, APPLICATION> toApplication;
  // when set, with() also stores the application value next to the agent value, so that get()
  // returns that same instance instead of converting the agent value again
  @Nullable private final ContextKey<BridgedValue<APPLICATION, AGENT>> bridgedValueKey;

  // TODO: maybe add a builder instead of all those constructors?
  ContextKeyBridge(
      String applicationKeyHolderClassName,
      String agentKeyHolderClassName,
      Function<AGENT, APPLICATION> toApplication,
      Function<APPLICATION, AGENT> toAgent)
      throws Throwable {
    this(applicationKeyHolderClassName, agentKeyHolderClassName, "KEY", toApplication, toAgent);
  }

  ContextKeyBridge(
      String applicationKeyHolderClassName,
      String agentKeyHolderClassName,
      Function<AGENT, APPLICATION> toApplication,
      Function<APPLICATION, AGENT> toAgent,
      boolean keepApplicationValue)
      throws Throwable {
    this(
        Class.forName(applicationKeyHolderClassName),
        Class.forName(agentKeyHolderClassName),
        "KEY",
        "KEY",
        toApplication,
        toAgent,
        keepApplicationValue);
  }

  ContextKeyBridge(
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

  ContextKeyBridge(
      Class<?> applicationKeyHolderClass,
      Class<?> agentKeyHolderClass,
      String applicationFieldName,
      String agentFieldName,
      Function<AGENT, APPLICATION> toApplication,
      Function<APPLICATION, AGENT> toAgent)
      throws Throwable {
    this(
        applicationKeyHolderClass,
        agentKeyHolderClass,
        applicationFieldName,
        agentFieldName,
        toApplication,
        toAgent,
        false);
  }

  @SuppressWarnings("unchecked") // casting reflection results
  private ContextKeyBridge(
      Class<?> applicationKeyHolderClass,
      Class<?> agentKeyHolderClass,
      String applicationFieldName,
      String agentFieldName,
      Function<AGENT, APPLICATION> toApplication,
      Function<APPLICATION, AGENT> toAgent,
      boolean keepApplicationValue)
      throws Throwable {

    Field applicationContextKeyField =
        applicationKeyHolderClass.getDeclaredField(applicationFieldName);
    applicationContextKeyField.setAccessible(true);
    this.applicationContextKey =
        (application.io.opentelemetry.context.ContextKey<APPLICATION>)
            applicationContextKeyField.get(null);

    Field agentContextKeyField = agentKeyHolderClass.getDeclaredField(agentFieldName);
    agentContextKeyField.setAccessible(true);
    this.agentContextKey = (ContextKey<AGENT>) agentContextKeyField.get(null);

    this.toApplication = toApplication;
    this.toAgent = toAgent;
    this.bridgedValueKey =
        keepApplicationValue ? ContextKey.named("bridged-" + agentContextKey) : null;
  }

  @Nullable
  <V> V get(
      AgentContextWrapper contextWrapper,
      application.io.opentelemetry.context.ContextKey<V> requestedKey) {
    if (requestedKey == applicationContextKey) {
      AGENT agentValue = contextWrapper.agentContext.get(agentContextKey);
      if (agentValue == null) {
        return null;
      }
      APPLICATION applicationValue = null;
      if (bridgedValueKey != null) {
        BridgedValue<APPLICATION, AGENT> bridgedValue =
            contextWrapper.agentContext.get(bridgedValueKey);
        // agent code may have replaced the value since, so check it is still the same one
        if (bridgedValue != null && bridgedValue.agentValue == agentValue) {
          applicationValue = bridgedValue.applicationValue;
        }
      }
      if (applicationValue == null) {
        applicationValue = toApplication.apply(agentValue);
      }
      @SuppressWarnings("unchecked") // fine
      V castValue = (V) applicationValue;
      return castValue;
    }
    return null;
  }

  @Nullable
  <V> application.io.opentelemetry.context.Context with(
      AgentContextWrapper contextWrapper,
      application.io.opentelemetry.context.ContextKey<V> requestedKey,
      V value) {
    if (requestedKey == applicationContextKey) {
      @SuppressWarnings("unchecked") // fine
      APPLICATION applicationValue = (APPLICATION) value;
      AGENT agentValue = toAgent.apply(applicationValue);
      if (agentValue == null) {
        return contextWrapper;
      }
      Context agentContext = contextWrapper.agentContext.with(agentContextKey, agentValue);
      if (bridgedValueKey != null) {
        agentContext =
            agentContext.with(bridgedValueKey, new BridgedValue<>(applicationValue, agentValue));
      }
      return new AgentContextWrapper(agentContext, contextWrapper.applicationContext);
    }
    return null;
  }

  private static final class BridgedValue<APPLICATION, AGENT> {
    final APPLICATION applicationValue;
    final AGENT agentValue;

    BridgedValue(APPLICATION applicationValue, AGENT agentValue) {
      this.applicationValue = applicationValue;
      this.agentValue = agentValue;
    }
  }
}
