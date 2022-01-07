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

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.server.ServerSpan;
import io.opentelemetry.javaagent.instrumentation.apachecamel.CamelDirection;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.MalformedURLException;
import java.net.URL;
import javax.annotation.Nullable;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class HttpSpanDecorator extends BaseSpanDecorator {

  private static final String POST_METHOD = "POST";
  private static final String GET_METHOD = "GET";

  protected String getProtocol() {
    return "http";
  }

  protected static String getHttpMethod(Exchange exchange, Endpoint endpoint) {
    // 1. Use method provided in header.
    Object method = exchange.getIn().getHeader(Exchange.HTTP_METHOD);
    if (method instanceof String) {
      return (String) method;
    }

    // 2. GET if query string is provided in header.
    if (exchange.getIn().getHeader(Exchange.HTTP_QUERY) != null) {
      return GET_METHOD;
    }

    // 3. GET if endpoint is configured with a query string.
    if (endpoint.getEndpointUri().indexOf('?') != -1) {
      return GET_METHOD;
    }

    // 4. POST if there is data to send (body is not null).
    if (exchange.getIn().getBody() != null) {
      return POST_METHOD;
    }

    // 5. GET otherwise.
    return GET_METHOD;
  }

  @Override
  public String getOperationName(
      Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {
    // Based on HTTP component documentation:
    String spanName = null;
    if (shouldSetPathAsName(camelDirection)) {
      spanName = getPath(exchange, endpoint);
    }
    return (spanName == null ? getHttpMethod(exchange, endpoint) : spanName);
  }

  @Override
  public void pre(
      AttributesBuilder attributes,
      Exchange exchange,
      Endpoint endpoint,
      CamelDirection camelDirection) {
    super.pre(attributes, exchange, endpoint, camelDirection);

    String httpUrl = getHttpUrl(exchange, endpoint);
    if (httpUrl != null) {
      attributes.put(SemanticAttributes.HTTP_URL, httpUrl);
    }

    attributes.put(SemanticAttributes.HTTP_METHOD, getHttpMethod(exchange, endpoint));
  }

  private static boolean shouldSetPathAsName(CamelDirection camelDirection) {
    return CamelDirection.INBOUND.equals(camelDirection);
  }

  @Nullable
  protected String getPath(Exchange exchange, Endpoint endpoint) {

    String httpUrl = getHttpUrl(exchange, endpoint);
    try {
      URL url = new URL(httpUrl);
      return url.getPath();
    } catch (MalformedURLException e) {
      return null;
    }
  }

  private static boolean shouldUpdateServerSpanName(
      Span serverSpan, CamelDirection camelDirection) {
    return (serverSpan != null && shouldSetPathAsName(camelDirection));
  }

  private void updateServerSpanName(Span serverSpan, Exchange exchange, Endpoint endpoint) {
    String path = getPath(exchange, endpoint);
    if (path != null) {
      serverSpan.updateName(path);
    }
  }

  @Override
  public void updateServerSpanName(
      Context context, Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (shouldUpdateServerSpanName(serverSpan, camelDirection)) {
      updateServerSpanName(serverSpan, exchange, endpoint);
    }
  }

  protected String getHttpUrl(Exchange exchange, Endpoint endpoint) {
    Object url = exchange.getIn().getHeader(Exchange.HTTP_URL);
    if (url instanceof String) {
      return (String) url;
    } else {
      Object uri = exchange.getIn().getHeader(Exchange.HTTP_URI);
      if (uri instanceof String) {
        return (String) uri;
      } else {
        // Try to obtain from endpoint
        int index = endpoint.getEndpointUri().lastIndexOf(getProtocol() + ":");
        if (index != -1) {
          return endpoint.getEndpointUri().substring(index);
        }
      }
    }
    return null;
  }

  @Override
  public void post(AttributesBuilder attributes, Exchange exchange, Endpoint endpoint) {
    super.post(attributes, exchange, endpoint);

    if (exchange.hasOut()) {
      Object responseCode = exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE);
      if (responseCode instanceof Integer) {
        attributes.put(SemanticAttributes.HTTP_STATUS_CODE, (Integer) responseCode);
      }
    }
  }
}
