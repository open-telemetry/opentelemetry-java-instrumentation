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

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static io.opentelemetry.javaagent.instrumentation.apachecamel.CamelSingletons.instrumenter;
import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.camel.Exchange;

/** Utility class for managing active contexts as a stack associated with an exchange. */
class ActiveContextManager {

  private static final String ACTIVE_CONTEXT_PROPERTY = "OpenTelemetry.activeContext";

  private static final Logger logger = Logger.getLogger(ActiveContextManager.class.getName());

  private ActiveContextManager() {}

  /**
   * This method activates the supplied context for the supplied exchange. If an existing context is
   * found for the exchange it will be pushed onto a stack.
   *
   * @param context The exchange
   * @param request The context
   */
  public static void activate(Context context, CamelRequest request) {
    Exchange exchange = request.getExchange();
    ContextWithScope parent = exchange.getProperty(ACTIVE_CONTEXT_PROPERTY, ContextWithScope.class);
    ContextWithScope contextWithScope = ContextWithScope.activate(parent, context, request);
    exchange.setProperty(ACTIVE_CONTEXT_PROPERTY, contextWithScope);
    logger.log(FINE, "Activated a span: {0}", contextWithScope);
  }

  /**
   * This method deactivates an existing active context associated with the supplied exchange. Once
   * deactivated, if a parent span is found associated with the stack for the exchange, it will be
   * restored as the current span for the exchange.
   *
   * @param exchange The exchange
   */
  public static Context deactivate(Exchange exchange) {
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

  private static class ContextWithScope {
    @Nullable private final ContextWithScope parent;
    @Nullable private final Context context;
    private final CamelRequest request;
    @Nullable private final Scope scope;

    public ContextWithScope(
        ContextWithScope parent, Context context, CamelRequest request, Scope scope) {
      this.parent = parent;
      this.context = context;
      this.request = request;
      this.scope = scope;
    }

    public static ContextWithScope activate(
        ContextWithScope parent, Context context, CamelRequest request) {
      Scope scope = context != null ? context.makeCurrent() : null;
      return new ContextWithScope(parent, context, request, scope);
    }

    public ContextWithScope getParent() {
      return parent;
    }

    public void deactivate(Exception exception) {
      if (scope == null) {
        return;
      }
      scope.close();
      instrumenter().end(context, request, null, exception);
    }

    @Override
    public String toString() {
      return "ContextWithScope [context=" + context + ", scope=" + scope + "]";
    }
  }
}
