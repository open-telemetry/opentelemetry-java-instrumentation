/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticsearchTransportExperimentalAttributesExtractor;
import org.elasticsearch.action.DocumentRequest;

public class Elasticsearch5TransportExperimentalAttributesExtractor
    extends ElasticsearchTransportExperimentalAttributesExtractor {

  private static final AttributeKey<String> ELASTICSEARCH_REQUEST_WRITE_TYPE =
      AttributeKey.stringKey("elasticsearch.request.write.type");
  private static final AttributeKey<String> ELASTICSEARCH_REQUEST_WRITE_ROUTING =
      AttributeKey.stringKey("elasticsearch.request.write.routing");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ElasticTransportRequest transportRequest) {
    super.onStart(attributes, parentContext, transportRequest);

    Object request = transportRequest.getRequest();
    if (request instanceof DocumentRequest) {
      DocumentRequest<?> req = (DocumentRequest<?>) request;
      attributes.put(ELASTICSEARCH_REQUEST_WRITE_TYPE, req.type());
      attributes.put(ELASTICSEARCH_REQUEST_WRITE_ROUTING, req.routing());
    }
  }
}
