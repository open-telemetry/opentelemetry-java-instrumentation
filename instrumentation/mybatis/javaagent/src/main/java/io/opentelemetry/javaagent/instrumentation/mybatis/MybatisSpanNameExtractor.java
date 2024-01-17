/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class MybatisSpanNameExtractor implements SpanNameExtractor<MapperMethodRequest> {

  @Override
  public String extract(MapperMethodRequest request) {
    return request.getMapperName();
  }
}
