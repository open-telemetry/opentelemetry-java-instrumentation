package io.opentelemetry.javaagent.instrumentation.thrift;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

final class ThriftSpanNameExtractor implements  SpanNameExtractor<ThriftRequest>{
  @Override
  public String extract(ThriftRequest request) {

    return request.getMethodName();
  }
}

