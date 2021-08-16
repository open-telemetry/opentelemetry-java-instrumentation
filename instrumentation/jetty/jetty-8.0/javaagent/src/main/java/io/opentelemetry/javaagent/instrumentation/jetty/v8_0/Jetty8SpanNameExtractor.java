/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import javax.servlet.http.HttpServletRequest;

public class Jetty8SpanNameExtractor implements SpanNameExtractor<HttpServletRequest> {

  @Override
  public String extract(HttpServletRequest request) {
    return "HTTP " + request.getMethod();
  }
}
