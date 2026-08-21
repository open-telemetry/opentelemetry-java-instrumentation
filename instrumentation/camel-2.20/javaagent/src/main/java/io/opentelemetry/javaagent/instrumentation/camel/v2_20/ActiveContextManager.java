/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Apache Camel Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelSingletons.instrumenter;
import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.camel.Exchange;

/** Utility class for managing active contexts as a stack associated with an exchange. */
public class ActiveContextManager {

  private static final String ACTIVE_CONTEXT_PROPERTY = "OpenTelemetry.activeContext";
  private static final String SEND_SCOPE_PROPERTY = "OpenTelemetry.sendScope";

  private static final Logger logger = Logger.getLogger(ActiveContextManager.class.getName());

  private ActiveContextManager() {}

  /**
   * This method activates the supplied context for the supplied exchange. If an existing context is
   * found for the exchange it will be pushed onto a stack.
   *
   * @param context The exchange
   * @param request The context
   */
  static void activate(@Nullable Context context, CamelRequest request) {
    Exchange exchange = request.getExchange();
    ContextWithScope parent = exchange.getProperty(ACTIVE_CONTEXT_PROPERTY, ContextWithScope.class);
    ContextWithScope contextWithScope = ContextWithScope.activate(parent, context, request);
    exchange.setProperty(ACTIVE_CONTEXT_PROPERTY, contextWithScope);
    synchronized (exchange) {
      SendScope sendScope = exchange.getProperty(SEND_SCOPE_PROPERTY, SendScope.class);
      if (sendScope != null) {
        sendScope.addActivated(contextWithScope);
      }
    }
    logger.log(FINE, "Activated a span: {0}", contextWithScope);
  }

  /**
   * This method deactivates an existing active context associated with the supplied exchange. Once
   * deactivated, if a parent span is found associated with the stack for the exchange, it will be
   * restored as the current span for the exchange.
   *
   * @param exchange The exchange
   */
  @Nullable
  static Context deactivate(Exchange exchange) {
    ContextWithScope contextWithScope =
        exchange.getProperty(ACTIVE_CONTEXT_PROPERTY, ContextWithScope.class);

    if (contextWithScope != null) {
      contextWithScope.deactivate(exchange.getException());
      exchange.setProperty(ACTIVE_CONTEXT_PROPERTY, contextWithScope.getParent());
      logger.log(FINE, "Deactivated span: {0}", contextWithScope);
      return contextWithScope.context;
    }

    return null;
  }

  public static Object beginSend(Exchange exchange) {
    synchronized (exchange) {
      SendScope parent = exchange.getProperty(SEND_SCOPE_PROPERTY, SendScope.class);
      SendScope sendScope = new SendScope(parent);
      ContextWithScope contextWithScope =
          exchange.getProperty(ACTIVE_CONTEXT_PROPERTY, ContextWithScope.class);
      while (contextWithScope != null) {
        sendScope.capture(contextWithScope);
        contextWithScope = contextWithScope.getParent();
      }
      exchange.setProperty(SEND_SCOPE_PROPERTY, sendScope);
      return sendScope;
    }
  }

  public static void endSend(Exchange exchange, Object state, boolean closeScopes) {
    synchronized (exchange) {
      SendScope sendScope = (SendScope) state;
      sendScope.finish(closeScopes);

      SendScope current = exchange.getProperty(SEND_SCOPE_PROPERTY, SendScope.class);
      while (current != null && current.isComplete()) {
        current = current.parent;
      }
      exchange.setProperty(SEND_SCOPE_PROPERTY, current);
    }
  }

  private static final class SendScope {
    @Nullable private final SendScope parent;
    private final List<ContextWithScope> scopes = new ArrayList<>();
    private boolean complete;

    private SendScope(@Nullable SendScope parent) {
      this.parent = parent;
    }

    private synchronized void capture(ContextWithScope contextWithScope) {
      scopes.add(contextWithScope);
    }

    private synchronized void addActivated(ContextWithScope contextWithScope) {
      if (complete) {
        contextWithScope.closeScope();
      } else {
        scopes.add(0, contextWithScope);
      }
    }

    private synchronized void finish(boolean closeScopes) {
      complete = true;
      if (closeScopes) {
        for (ContextWithScope contextWithScope : scopes) {
          contextWithScope.closeScope();
        }
      }
    }

    private synchronized boolean isComplete() {
      return complete;
    }
  }

  private static class ContextWithScope {
    @Nullable private final ContextWithScope parent;
    @Nullable private final Context context;
    private final CamelRequest request;
    @Nullable private final Thread scopeOwner;
    @Nullable private Scope scope;

    ContextWithScope(
        @Nullable ContextWithScope parent,
        @Nullable Context context,
        CamelRequest request,
        @Nullable Thread scopeOwner,
        @Nullable Scope scope) {
      this.parent = parent;
      this.context = context;
      this.request = request;
      this.scopeOwner = scopeOwner;
      this.scope = scope;
    }

    static ContextWithScope activate(
        @Nullable ContextWithScope parent, @Nullable Context context, CamelRequest request) {
      Scope scope = context != null ? context.makeCurrent() : null;
      return new ContextWithScope(
          parent, context, request, scope == null ? null : Thread.currentThread(), scope);
    }

    @Nullable
    ContextWithScope getParent() {
      return parent;
    }

    void deactivate(@Nullable Exception exception) {
      closeScope();
      if (context != null) {
        instrumenter(request).end(context, request, null, exception);
      }
    }

    synchronized void closeScope() {
      if (scope != null && Thread.currentThread() == scopeOwner) {
        scope.close();
        scope = null;
      }
    }

    @Override
    public String toString() {
      return "ContextWithScope [context=" + context + ", scope=" + scope + "]";
    }
  }
}
