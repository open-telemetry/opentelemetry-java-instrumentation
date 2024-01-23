/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis.v3_2;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class MyBatisSpanNameExtractor implements SpanNameExtractor<MapperMethodRequest> {

  @Override
  public String extract(MapperMethodRequest request) {
    String mapperName = request.getMapperName();
    if (mapperName == null) {
      return "MyBatis execute";
    }
    // filter the package name in mapperName
    int lastDotIndex = mapperName.lastIndexOf('.');
    int secondLastDotIndex = mapperName.lastIndexOf('.', lastDotIndex - 1);
    return mapperName.substring(secondLastDotIndex + 1);
  }
}
