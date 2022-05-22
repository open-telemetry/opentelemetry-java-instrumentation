/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticsearchTransportExperimentalAttributesExtractor;
import org.elasticsearch.action.DocWriteRequest;

public class Elasticsearch53TransportExperimentalAttributesExtractor
    extends ElasticsearchTransportExperimentalAttributesExtractor {

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ElasticTransportRequest transportRequest) {
    super.onStart(attributes, parentContext, transportRequest);

    Object request = transportRequest.getRequest();
    if (request instanceof DocWriteRequest) {
      DocWriteRequest<?> req = (DocWriteRequest<?>) request;
      attributes.put("elasticsearch.request.write.type", req.type());
      attributes.put("elasticsearch.request.write.routing", req.routing());
      attributes.put("elasticsearch.request.write.version", req.version());
    }
  }
}
