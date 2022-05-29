/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class OpenfeignSpanNameExtractor implements SpanNameExtractor<ExecuteAndDecodeRequest> {
  @Override
  public String extract(ExecuteAndDecodeRequest request) {
    return request.getRequestTemplate().method() + " " + request.getTemplateUri().getHost();
  }
}
