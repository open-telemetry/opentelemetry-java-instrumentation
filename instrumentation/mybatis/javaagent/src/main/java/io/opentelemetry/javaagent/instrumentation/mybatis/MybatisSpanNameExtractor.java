package io.opentelemetry.javaagent.instrumentation.mybatis;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class MybatisSpanNameExtractor implements SpanNameExtractor<MapperMethodRequest> {

  @Override
  public String extract(MapperMethodRequest request) {
    return request.getMapperName();
  }
}
