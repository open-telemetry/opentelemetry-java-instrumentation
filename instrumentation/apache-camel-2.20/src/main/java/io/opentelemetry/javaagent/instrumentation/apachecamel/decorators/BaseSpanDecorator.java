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

import io.opentelemetry.javaagent.instrumentation.apachecamel.SpanDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/** An abstract base implementation of the {@link SpanDecorator} interface. */
class BaseSpanDecorator implements SpanDecorator {

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
  public String getOperationName(Exchange exchange, Endpoint endpoint) {
    String[] splitURI = StringHelper.splitOnCharacter(endpoint.getEndpointUri(), ":", 2);
    if (splitURI.length > 0) {
      return splitURI[0];
    } else {
      return null;
    }
  }

  @Override
  public void pre(Span span, Exchange exchange, Endpoint endpoint) {
    span.setAttribute("camel.uri", URISupport.sanitizeUri(endpoint.getEndpointUri()));
  }

  @Override
  public void post(Span span, Exchange exchange, Endpoint endpoint) {
    if (exchange.isFailed()) {
      span.setAttribute("error", true);
      if (exchange.getException() != null) {
        span.recordException(exchange.getException());
      }
    }
  }

  @Override
  public Span.Kind getInitiatorSpanKind() {
    return Kind.CLIENT;
  }

  @Override
  public Span.Kind getReceiverSpanKind() {
    return Kind.SERVER;
  }
}
