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

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

/** This interface represents a decorator specific to the component/endpoint being instrumented. */
public interface SpanDecorator {

  /**
   * This method indicates whether the component associated with the SpanDecorator should result in
   * a new span being created.
   *
   * @return Whether a new span should be created
   */
  boolean shouldStartNewSpan();

  /**
   * Returns the operation name to use with the Span representing this exchange and endpoint.
   *
   * @param exchange The exchange
   * @param endpoint The endpoint
   * @return The operation name
   */
  String getOperationName(Exchange exchange, Endpoint endpoint, CamelDirection camelDirection);

  /**
   * This method adds appropriate details (tags/logs) to the supplied span based on the pre
   * processing of the exchange.
   *
   * @param attributes The span attributes
   * @param exchange The exchange
   * @param endpoint The endpoint
   */
  void pre(
      AttributesBuilder attributes,
      Exchange exchange,
      Endpoint endpoint,
      CamelDirection camelDirection);

  /**
   * This method adds appropriate details (tags/logs) to the supplied span based on the post
   * processing of the exchange.
   *
   * @param attributes The span attributes
   * @param exchange The exchange
   * @param endpoint The endpoint
   */
  void post(AttributesBuilder attributes, Exchange exchange, Endpoint endpoint);

  /** Returns the 'span.kind' value for use when the component is initiating a communication. */
  SpanKind getInitiatorSpanKind();

  /** Returns the 'span.kind' value for use when the component is receiving a communication. */
  SpanKind getReceiverSpanKind();

  void updateServerSpanName(
      Context context, Exchange exchange, Endpoint endpoint, CamelDirection camelDirection);
}
