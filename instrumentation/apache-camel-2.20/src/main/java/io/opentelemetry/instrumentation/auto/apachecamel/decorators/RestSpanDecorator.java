/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.apachecamel.decorators;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import io.opentelemetry.trace.Span;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RestSpanDecorator extends HttpSpanDecorator {

  private static final Logger LOG = LoggerFactory.getLogger(RestSpanDecorator.class);

  protected static String getPath(String uri) {
    // Obtain the 'path' part of the URI format: rest://method:path[:uriTemplate]?[options]
    String path = null;
    int index = uri.indexOf(':');
    if (index != -1) {
      index = uri.indexOf(':', index + 1);
      if (index != -1) {
        path = uri.substring(index + 1);
        index = path.indexOf('?');
        if (index != -1) {
          path = path.substring(0, index);
        }
        path = path.replaceAll(":", "");
        try {
          path = URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
          LOG.debug("Failed to decode URL path '" + path + "', ignoring exception", e);
        }
      }
    }
    return path;
  }

  protected static List<String> getParameters(String path) {
    List<String> parameters = null;

    int startIndex = path.indexOf('{');
    while (startIndex != -1) {
      int endIndex = path.indexOf('}', startIndex);
      if (endIndex != -1) {
        if (parameters == null) {
          parameters = new ArrayList<>();
        }
        parameters.add(path.substring(startIndex + 1, endIndex));
        startIndex = path.indexOf('{', endIndex);
      } else {
        // Break out of loop as no valid end token
        startIndex = -1;
      }
    }

    return parameters == null ? Collections.emptyList() : parameters;
  }

  @Override
  public String getOperationName(Exchange exchange, Endpoint endpoint) {
    return getPath(endpoint.getEndpointUri());
  }

  @Override
  public void pre(Span span, Exchange exchange, Endpoint endpoint) {
    super.pre(span, exchange, endpoint);

    getParameters(getPath(endpoint.getEndpointUri()))
        .forEach(
            param -> {
              Object value = exchange.getIn().getHeader(param);
              if (value != null) {
                span.setAttribute(param, (String) value);
              }
            });
  }
}
