/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class HttpSpanDecorator extends BaseSpanDecorator {

  private static final String POST_METHOD = "POST";
  private static final String GET_METHOD = "GET";

  public static String getHttpMethod(Exchange exchange, Endpoint endpoint) {
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
  public String getOperationName(Exchange exchange, Endpoint endpoint) {
    // Based on HTTP component documentation:
    return getHttpMethod(exchange, endpoint);
  }

  @Override
  public void pre(Span span, Exchange exchange, Endpoint endpoint) {
    super.pre(span, exchange, endpoint);

    String httpUrl = getHttpURL(exchange, endpoint);
    if (httpUrl != null) {
      span.setAttribute(SemanticAttributes.HTTP_URL, httpUrl);
    }

    span.setAttribute(SemanticAttributes.HTTP_METHOD, getHttpMethod(exchange, endpoint));
  }

  protected String getHttpURL(Exchange exchange, Endpoint endpoint) {
    Object url = exchange.getIn().getHeader(Exchange.HTTP_URL);
    if (url instanceof String) {
      return (String) url;
    } else {
      Object uri = exchange.getIn().getHeader(Exchange.HTTP_URI);
      if (uri instanceof String) {
        return (String) uri;
      } else {
        // Try to obtain from endpoint
        int index = endpoint.getEndpointUri().lastIndexOf("http:");
        if (index != -1) {
          return endpoint.getEndpointUri().substring(index);
        }
      }
    }
    return null;
  }

  @Override
  public void post(Span span, Exchange exchange, Endpoint endpoint) {
    super.post(span, exchange, endpoint);

    if (exchange.hasOut()) {
      Object responseCode = exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE);
      if (responseCode instanceof Integer) {
        span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, (Integer) responseCode);
      }
    }
  }
}
