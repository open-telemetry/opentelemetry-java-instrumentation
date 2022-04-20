/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.instrumentationapi;

import application.io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.ContextKeyBridge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class InstrumentationApiContextBridging {

  public static List<ContextKeyBridge<?, ?>> instrumentationApiBridges() {
    List<ContextKeyBridge<?, ?>> bridges = new ArrayList<>();

    try {
      bridges.add(
          new ContextKeyBridge<Span, io.opentelemetry.api.trace.Span>(
              "application.io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan",
              "io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan",
              Bridging::toApplication,
              Bridging::toAgentOrNull));
    } catch (Throwable e) {
      // no instrumentation-api on classpath
    }
    try {
      // old SERVER_KEY bridge - needed to make legacy ServerSpan work, for users who're using old
      // instrumentation-api version with the newest agent version
      bridges.add(
          new ContextKeyBridge<Span, io.opentelemetry.api.trace.Span>(
              "application.io.opentelemetry.instrumentation.api.internal.SpanKey",
              "io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan",
              "SERVER_KEY",
              "KEY",
              Bridging::toApplication,
              Bridging::toAgentOrNull));
    } catch (Throwable e) {
      // no old instrumentation-api on classpath
    }

    List<String> spanKeyNames =
        Arrays.asList(
            // span kind keys
            "KIND_SERVER_KEY",
            "KIND_CLIENT_KEY",
            "KIND_CONSUMER_KEY",
            "KIND_PRODUCER_KEY",
            // semantic convention keys
            "HTTP_SERVER_KEY",
            "RPC_SERVER_KEY",
            "HTTP_CLIENT_KEY",
            "RPC_CLIENT_KEY",
            "DB_CLIENT_KEY",
            "PRODUCER_KEY",
            "CONSUMER_RECEIVE_KEY",
            "CONSUMER_PROCESS_KEY");

    for (String spanKeyName : spanKeyNames) {
      ContextKeyBridge<?, ?> spanKeyBridge = spanKeyBridge(spanKeyName);
      if (spanKeyBridge != null) {
        bridges.add(spanKeyBridge);
      }
    }

    ContextKeyBridge<?, ?> httpRouteHolderBridge = httpRouteHolderBridge();
    if (httpRouteHolderBridge != null) {
      bridges.add(httpRouteHolderBridge);
    }

    return bridges;
  }

  @Nullable
  private static ContextKeyBridge<Span, io.opentelemetry.api.trace.Span> spanKeyBridge(
      String name) {
    try {
      return new ContextKeyBridge<>(
          "application.io.opentelemetry.instrumentation.api.internal.SpanKey",
          "io.opentelemetry.instrumentation.api.internal.SpanKey",
          name,
          Bridging::toApplication,
          Bridging::toAgentOrNull);
    } catch (Throwable e) {
      // instrumentation-api may be absent on the classpath, just skip
      return null;
    }
  }

  private static final Class<?> AGENT_HTTP_ROUTE_HOLDER;
  private static final MethodHandle AGENT_CREATE;
  private static final MethodHandle AGENT_GET_UPDATED_BY_SOURCE_ORDER;
  private static final MethodHandle AGENT_GET_ROUTE;

  private static final Class<?> APPLICATION_HTTP_ROUTE_HOLDER;
  private static final MethodHandle APPLICATION_CREATE;
  private static final MethodHandle APPLICATION_GET_UPDATED_BY_SOURCE_ORDER;
  private static final MethodHandle APPLICATION_GET_ROUTE;

  static {
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    Class<?> agentHttpRouteState = null;
    MethodHandle agentCreate = null;
    MethodHandle agentGetUpdatedBySourceOrder = null;
    MethodHandle agentGetRoute = null;
    Class<?> applicationHttpRouteState = null;
    MethodHandle applicationCreate = null;
    MethodHandle applicationGetUpdatedBySourceOrder = null;
    MethodHandle applicationGetRoute = null;

    try {
      agentHttpRouteState =
          Class.forName("io.opentelemetry.instrumentation.api.internal.HttpRouteState");
      agentCreate =
          lookup.findStatic(
              agentHttpRouteState,
              "create",
              MethodType.methodType(agentHttpRouteState, int.class, String.class));
      agentGetUpdatedBySourceOrder =
          lookup.findVirtual(
              agentHttpRouteState, "getUpdatedBySourceOrder", MethodType.methodType(int.class));
      agentGetRoute =
          lookup.findVirtual(agentHttpRouteState, "getRoute", MethodType.methodType(String.class));

      applicationHttpRouteState =
          Class.forName("application.io.opentelemetry.instrumentation.api.internal.HttpRouteState");
      applicationCreate =
          lookup.findStatic(
              applicationHttpRouteState,
              "create",
              MethodType.methodType(applicationHttpRouteState, int.class, String.class));
      applicationGetUpdatedBySourceOrder =
          lookup.findVirtual(
              applicationHttpRouteState,
              "getUpdatedBySourceOrder",
              MethodType.methodType(int.class));
      applicationGetRoute =
          lookup.findVirtual(
              applicationHttpRouteState, "getRoute", MethodType.methodType(String.class));
    } catch (Throwable ignored) {
      // instrumentation-api may be absent on the classpath, or it might be an older version
    }

    AGENT_HTTP_ROUTE_HOLDER = agentHttpRouteState;
    AGENT_CREATE = agentCreate;
    AGENT_GET_UPDATED_BY_SOURCE_ORDER = agentGetUpdatedBySourceOrder;
    AGENT_GET_ROUTE = agentGetRoute;
    APPLICATION_HTTP_ROUTE_HOLDER = applicationHttpRouteState;
    APPLICATION_CREATE = applicationCreate;
    APPLICATION_GET_UPDATED_BY_SOURCE_ORDER = applicationGetUpdatedBySourceOrder;
    APPLICATION_GET_ROUTE = applicationGetRoute;
  }

  @Nullable
  private static ContextKeyBridge<?, ?> httpRouteHolderBridge() {
    if (APPLICATION_HTTP_ROUTE_HOLDER == null
        || APPLICATION_CREATE == null
        || APPLICATION_GET_UPDATED_BY_SOURCE_ORDER == null
        || APPLICATION_GET_ROUTE == null) {
      // HttpRouteHolder not on application classpath; or an old version of it
      return null;
    }
    try {
      return new ContextKeyBridge<>(
          APPLICATION_HTTP_ROUTE_HOLDER,
          AGENT_HTTP_ROUTE_HOLDER,
          "KEY",
          "KEY",
          httpRouteHolderConvert(
              APPLICATION_CREATE, AGENT_GET_UPDATED_BY_SOURCE_ORDER, AGENT_GET_ROUTE),
          httpRouteHolderConvert(
              AGENT_CREATE, APPLICATION_GET_UPDATED_BY_SOURCE_ORDER, APPLICATION_GET_ROUTE));
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Function<Object, Object> httpRouteHolderConvert(
      MethodHandle create, MethodHandle getUpdatedBySourceOrder, MethodHandle getRoute) {
    return httpRouteHolder -> {
      try {
        int updatedBySourceOrder = (int) getUpdatedBySourceOrder.invoke(httpRouteHolder);
        String route = (String) getRoute.invoke(httpRouteHolder);
        return create.invoke(updatedBySourceOrder, route);
      } catch (Throwable e) {
        return null;
      }
    };
  }

  private InstrumentationApiContextBridging() {}
}
