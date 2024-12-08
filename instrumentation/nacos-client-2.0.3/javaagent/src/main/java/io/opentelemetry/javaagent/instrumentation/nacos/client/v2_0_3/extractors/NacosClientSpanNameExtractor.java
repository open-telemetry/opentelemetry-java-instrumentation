package io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.extractors;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.NacosClientRequest;

public class NacosClientSpanNameExtractor implements SpanNameExtractor<NacosClientRequest> {
  @Override
  public String extract(NacosClientRequest nacosClientRequest) {
    return nacosClientRequest.getSpanName();
  }

}
