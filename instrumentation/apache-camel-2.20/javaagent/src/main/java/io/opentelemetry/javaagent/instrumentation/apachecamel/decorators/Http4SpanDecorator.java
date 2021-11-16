/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import javax.annotation.Nullable;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class Http4SpanDecorator extends HttpSpanDecorator {
  @Nullable
  @Override
  protected String getHttpUrl(Exchange exchange, Endpoint endpoint) {
    Object url = exchange.getIn().getHeader(Exchange.HTTP_URL);
    if (url instanceof String) {
      return ((String) url).replace("http4", "http");
    } else {
      Object uri = exchange.getIn().getHeader(Exchange.HTTP_URI);
      if (uri instanceof String) {
        return ((String) uri).replace("http4", "http");
      } else {
        // Try to obtain from endpoint
        int index = endpoint.getEndpointUri().lastIndexOf("http4:");
        if (index != -1) {
          return endpoint.getEndpointUri().substring(index).replace("http4", "http");
        }
      }
    }
    return null;
  }
}
