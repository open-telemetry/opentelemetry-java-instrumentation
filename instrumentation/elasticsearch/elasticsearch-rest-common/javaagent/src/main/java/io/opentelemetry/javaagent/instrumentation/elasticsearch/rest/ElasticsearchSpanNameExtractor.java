package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class ElasticsearchSpanNameExtractor implements SpanNameExtractor<ElasticsearchRestRequest> {

  private final ElasticsearchDbAttributesGetter dbAttributesGetter;

  public ElasticsearchSpanNameExtractor(ElasticsearchDbAttributesGetter dbAttributesGetter) {
    this.dbAttributesGetter = dbAttributesGetter;
  }

  @Override
  public String extract(ElasticsearchRestRequest elasticsearchRestRequest) {
    String name = dbAttributesGetter.getOperation(elasticsearchRestRequest);
    return name != null ? name : elasticsearchRestRequest.getMethod();
  }
}
