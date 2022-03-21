/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.shenyu;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class ShenYuSpanNameExtractor implements SpanNameExtractor<Object> {

  @Override
  public String extract(Object plugin) {
    return plugin.getClass().getSimpleName();
  }

}
