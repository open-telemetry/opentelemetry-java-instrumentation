/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import javax.annotation.Nullable;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class Http4SpanDecorator extends HttpSpanDecorator {
  @Override
  protected String getProtocol() {
    return "http4";
  }

  @Nullable
  @Override
  protected String getHttpUrl(Exchange exchange, Endpoint endpoint) {
    String url = super.getHttpUrl(exchange, endpoint);
    if (url != null) {
      return url.replace(getProtocol(), getProtocol().substring(0, getProtocol().length() - 1));
    }
    return null;
  }
}
