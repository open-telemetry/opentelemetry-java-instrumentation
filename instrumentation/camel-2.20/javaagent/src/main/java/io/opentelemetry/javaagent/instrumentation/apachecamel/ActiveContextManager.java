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

import static java.util.logging.Level.FINE;

import io.opentelemetry.javaagent.bootstrap.apachecamel.ContextWithScope;
import io.opentelemetry.context.Context;
import java.util.logging.Logger;
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
   * @param context The context
   * @param exchange The exchange
   */
  public static void activate(Context context, Exchange exchange) {
    ContextWithScope parent = exchange.getProperty(ACTIVE_CONTEXT_PROPERTY, ContextWithScope.class);
    ContextWithScope contextWithScope = ContextWithScope.activate(parent, context);
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
      contextWithScope.deactivate();
      exchange.setProperty(ACTIVE_CONTEXT_PROPERTY, contextWithScope.getParent());
      logger.log(FINE, "Deactivated span: {0}", contextWithScope);
      return contextWithScope.getContext();
    }

    return null;
  }

}
