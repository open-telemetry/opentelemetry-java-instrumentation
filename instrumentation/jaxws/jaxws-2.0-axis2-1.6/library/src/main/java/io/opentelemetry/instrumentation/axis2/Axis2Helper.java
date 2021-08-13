/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.axis2;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;
import static io.opentelemetry.instrumentation.axis2.Axis2Singletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import org.apache.axis2.jaxws.core.MessageContext;

public final class Axis2Helper {
  private static final String REQUEST_KEY = Axis2Helper.class.getName() + ".Request";
  private static final String CONTEXT_KEY = Axis2Helper.class.getName() + ".Context";
  private static final String SCOPE_KEY = Axis2Helper.class.getName() + ".Scope";

  private Axis2Helper() {}

  public static void start(MessageContext message) {
    Context parentContext = Context.current();

    Axis2Request request = new Axis2Request(message);
    ServerSpanNaming.updateServerSpanName(
        parentContext,
        CONTROLLER,
        Axis2ServerSpanNaming.getServerSpanNameSupplier(parentContext, request));

    if (!instrumenter().shouldStart(parentContext, request)) {
      return;
    }

    Context context = instrumenter().start(parentContext, request);
    Scope scope = context.makeCurrent();

    message.setProperty(REQUEST_KEY, request);
    message.setProperty(CONTEXT_KEY, context);
    message.setProperty(SCOPE_KEY, scope);
  }

  public static void end(MessageContext message, Throwable throwable) {
    Axis2Request request = (Axis2Request) message.getProperty(REQUEST_KEY);
    if (request == null) {
      return;
    }

    Scope scope = (Scope) message.getProperty(SCOPE_KEY);
    if (scope == null) {
      return;
    }

    scope.close();
    Context context = (Context) message.getProperty(CONTEXT_KEY);

    message.setProperty(REQUEST_KEY, null);
    message.setProperty(CONTEXT_KEY, null);
    message.setProperty(SCOPE_KEY, null);

    instrumenter().end(context, request, null, throwable);
  }

  public static String getSpanName(MessageContext message) {
    org.apache.axis2.context.MessageContext axisMessageContext = message.getAxisMessageContext();
    String serviceName = axisMessageContext.getOperationContext().getServiceName();
    String operationName = axisMessageContext.getOperationContext().getOperationName();
    return serviceName + "/" + operationName;
  }
}
