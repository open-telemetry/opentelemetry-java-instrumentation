/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.ElasticsearchTransportInstrumenterFactory;
import org.elasticsearch.action.ActionResponse;

class Elasticsearch5TransportSingletons {

  private static final Instrumenter<ElasticTransportRequest, ActionResponse> instrumenter =
      ElasticsearchTransportInstrumenterFactory.create(
          "io.opentelemetry.elasticsearch-transport-5.0",
          new Elasticsearch5TransportExperimentalAttributesExtractor(),
          new Elasticsearch5TransportAttributesGetter());

  static Instrumenter<ElasticTransportRequest, ActionResponse> instrumenter() {
    return instrumenter;
  }

  private Elasticsearch5TransportSingletons() {}
}
