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

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.instrumentation.apachecamel.CamelDirection;
import io.opentelemetry.javaagent.instrumentation.apachecamel.SpanDecorator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/** An abstract base implementation of the {@link SpanDecorator} interface. */
class BaseSpanDecorator implements SpanDecorator {

  static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBoolean("otel.instrumentation.apache-camel.experimental-span-attributes", false);

  static final String DEFAULT_OPERATION_NAME = "CamelOperation";

  /**
   * This method removes the scheme, any leading slash characters and options from the supplied URI.
   * This is intended to extract a meaningful name from the URI that can be used in situations, such
   * as the operation name.
   *
   * @param endpoint The endpoint
   * @return The stripped value from the URI
   */
  public static String stripSchemeAndOptions(Endpoint endpoint) {
    int start = endpoint.getEndpointUri().indexOf(':');
    start++;
    // Remove any leading '/'
    while (endpoint.getEndpointUri().charAt(start) == '/') {
      start++;
    }
    int end = endpoint.getEndpointUri().indexOf('?');
    return end == -1
        ? endpoint.getEndpointUri().substring(start)
        : endpoint.getEndpointUri().substring(start, end);
  }

  public static Map<String, String> toQueryParameters(String uri) {
    int index = uri.indexOf('?');
    if (index != -1) {
      String queryString = uri.substring(index + 1);
      Map<String, String> map = new HashMap<>();
      for (String param : queryString.split("&")) {
        String[] parts = param.split("=");
        if (parts.length == 2) {
          map.put(parts[0], parts[1]);
        }
      }
      return map;
    }
    return Collections.emptyMap();
  }

  @Override
  public boolean shouldStartNewSpan() {
    return true;
  }

  @Override
  public String getOperationName(
      Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {
    String[] splitUri = StringHelper.splitOnCharacter(endpoint.getEndpointUri(), ":", 2);
    return (splitUri.length > 0 ? splitUri[0] : DEFAULT_OPERATION_NAME);
  }

  @Override
  public void pre(Span span, Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute("apache-camel.uri", URISupport.sanitizeUri(endpoint.getEndpointUri()));
    }
  }

  @Override
  public void post(Span span, Exchange exchange, Endpoint endpoint) {
    if (exchange.isFailed()) {
      span.setStatus(StatusCode.ERROR);
      if (exchange.getException() != null) {
        span.recordException(exchange.getException());
      }
    }
  }

  @Override
  public SpanKind getInitiatorSpanKind() {
    return SpanKind.CLIENT;
  }

  @Override
  public SpanKind getReceiverSpanKind() {
    return SpanKind.SERVER;
  }
}
