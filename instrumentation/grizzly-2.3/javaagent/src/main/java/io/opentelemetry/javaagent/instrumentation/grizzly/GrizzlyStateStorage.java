/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;

public final class GrizzlyStateStorage {

  private static final String REQUEST_ATTRIBUTE = GrizzlyStateStorage.class.getName() + ".request";
  private static final String CONTEXT_ATTRIBUTE = GrizzlyStateStorage.class.getName() + ".context";

  public static void attachContextAndRequest(
      FilterChainContext filterChainContext, Context context, HttpRequestPacket request) {
    filterChainContext.getAttributes().setAttribute(CONTEXT_ATTRIBUTE, context);
    filterChainContext.getAttributes().setAttribute(REQUEST_ATTRIBUTE, request);
  }

  @Nullable
  public static Context getContext(FilterChainContext filterChainContext) {
    Object attribute = filterChainContext.getAttributes().getAttribute(CONTEXT_ATTRIBUTE);
    return attribute instanceof Context ? (Context) attribute : null;
  }

  @Nullable
  public static Context removeContext(FilterChainContext filterChainContext) {
    Object attribute = filterChainContext.getAttributes().removeAttribute(CONTEXT_ATTRIBUTE);
    return attribute instanceof Context ? (Context) attribute : null;
  }

  @Nullable
  public static HttpRequestPacket removeRequest(FilterChainContext filterChainContext) {
    Object attribute = filterChainContext.getAttributes().removeAttribute(REQUEST_ATTRIBUTE);
    return attribute instanceof HttpRequestPacket ? (HttpRequestPacket) attribute : null;
  }

  private GrizzlyStateStorage() {}
}
