/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.ElasticsearchTransportInstrumenterFactory;
import org.elasticsearch.action.ActionResponse;

class Elasticsearch53TransportSingletons {

  private static final Instrumenter<ElasticTransportRequest, ActionResponse> instrumenter =
      ElasticsearchTransportInstrumenterFactory.create(
          "io.opentelemetry.elasticsearch-transport-5.3",
          new Elasticsearch53TransportExperimentalAttributesExtractor(),
          new Elasticsearch53TransportAttributesGetter());

  static Instrumenter<ElasticTransportRequest, ActionResponse> instrumenter() {
    return instrumenter;
  }

  private Elasticsearch53TransportSingletons() {}
}
