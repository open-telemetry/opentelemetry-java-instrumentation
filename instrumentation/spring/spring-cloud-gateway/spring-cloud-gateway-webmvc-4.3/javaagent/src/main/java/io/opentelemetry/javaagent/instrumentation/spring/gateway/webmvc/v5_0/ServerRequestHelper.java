/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.webmvc.v5_0;

import static io.opentelemetry.javaagent.instrumentation.spring.gateway.common.GatewayRouteHelper.ROUTE_ID_ATTRIBUTE;
import static io.opentelemetry.javaagent.instrumentation.spring.gateway.common.GatewayRouteHelper.ROUTE_URI_ATTRIBUTE;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.javaagent.instrumentation.spring.gateway.common.GatewayRouteHelper;
import java.lang.reflect.Field;
import javax.annotation.Nullable;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayDelegatingRouterFunction;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Helper class for extracting Spring Cloud Gateway Server WebMVC route information from
 * ServerRequest and adding it to spans.
 */
public final class ServerRequestHelper {
  @Nullable private static final Field routeIdField;

  static {
    Field routeIdField1 = null;
    try {
      routeIdField1 = GatewayDelegatingRouterFunction.class.getDeclaredField("routeId");
      routeIdField1.setAccessible(true);
    } catch (NoSuchFieldException ignored) {
      // Ignored
    }

    routeIdField = routeIdField1;
  }

  public static void extractAttributes(
      Object gatewayRouterFunction, ServerRequest request, Context context) {
    if (routeIdField == null || !GatewayRouteHelper.shouldCaptureExperimentalSpanAttributes()) {
      return;
    }

    Span serverSpan = LocalRootSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    try {
      String routeId = (String) routeIdField.get(gatewayRouterFunction);
      String convergedRouteId = GatewayRouteHelper.convergeRouteId(routeId);
      if (convergedRouteId != null) {
        serverSpan.setAttribute(ROUTE_ID_ATTRIBUTE, convergedRouteId);
      }
    } catch (Exception e) {
      // Silently ignore
    }

    request
        .attribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR)
        .ifPresent(uri -> serverSpan.setAttribute(ROUTE_URI_ATTRIBUTE, uri.toString()));
  }

  private ServerRequestHelper() {}
}
