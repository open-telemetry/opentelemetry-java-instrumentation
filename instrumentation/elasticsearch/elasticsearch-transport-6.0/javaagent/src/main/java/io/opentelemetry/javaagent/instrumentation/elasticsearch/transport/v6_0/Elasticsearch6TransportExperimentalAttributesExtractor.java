/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticsearchTransportExperimentalAttributesExtractor;
import org.elasticsearch.action.DocWriteRequest;

public class Elasticsearch6TransportExperimentalAttributesExtractor
    extends ElasticsearchTransportExperimentalAttributesExtractor {

  @Override
  public void onStart(AttributesBuilder attributes, ElasticTransportRequest transportRequest) {
    super.onStart(attributes, transportRequest);

    Object request = transportRequest.getRequest();
    if (request instanceof DocWriteRequest) {
      DocWriteRequest<?> req = (DocWriteRequest<?>) request;
      attributes.put("elasticsearch.request.write.type", req.type());
      attributes.put("elasticsearch.request.write.routing", req.routing());
      attributes.put("elasticsearch.request.write.version", req.version());
    }
  }
}
