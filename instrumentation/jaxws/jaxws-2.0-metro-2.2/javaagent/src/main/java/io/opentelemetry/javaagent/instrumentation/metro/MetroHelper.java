/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import static io.opentelemetry.javaagent.instrumentation.metro.MetroSingletons.instrumenter;

import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.server.WSEndpoint;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public final class MetroHelper {
  private static final String REQUEST_KEY = MetroHelper.class.getName() + ".Request";
  private static final String CONTEXT_KEY = MetroHelper.class.getName() + ".Context";
  private static final String SCOPE_KEY = MetroHelper.class.getName() + ".Scope";
  private static final String THROWABLE_KEY = MetroHelper.class.getName() + ".Throwable";

  private MetroHelper() {}

  public static void start(WSEndpoint<?> endpoint, Packet packet) {
    Context parentContext = Context.current();

    MetroRequest request = new MetroRequest(endpoint, packet);
    MetroServerSpanNaming.updateServerSpanName(parentContext, request);

    if (!instrumenter().shouldStart(parentContext, request)) {
      return;
    }

    Context context = instrumenter().start(parentContext, request);
    Scope scope = context.makeCurrent();

    // store context and scope
    packet.invocationProperties.put(REQUEST_KEY, request);
    packet.invocationProperties.put(CONTEXT_KEY, context);
    packet.invocationProperties.put(SCOPE_KEY, scope);
  }

  public static void end(Packet packet) {
    end(packet, null);
  }

  public static void end(Packet packet, Throwable throwable) {
    Scope scope = (Scope) packet.invocationProperties.remove(SCOPE_KEY);
    if (scope == null) {
      return;
    }
    scope.close();

    MetroRequest request = (MetroRequest) packet.invocationProperties.remove(REQUEST_KEY);
    Context context = (Context) packet.invocationProperties.remove(CONTEXT_KEY);
    if (throwable == null) {
      throwable = (Throwable) packet.invocationProperties.remove(THROWABLE_KEY);
    }
    instrumenter().end(context, request, null, throwable);
  }

  public static void storeThrowable(Packet packet, Throwable throwable) {
    packet.invocationProperties.put(THROWABLE_KEY, throwable);
  }
}
