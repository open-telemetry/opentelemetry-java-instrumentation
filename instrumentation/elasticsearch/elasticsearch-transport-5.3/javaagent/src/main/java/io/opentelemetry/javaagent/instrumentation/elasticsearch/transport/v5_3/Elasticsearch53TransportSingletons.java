/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportNetworkAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticsearchTransportInstrumenterFactory;
import org.elasticsearch.action.ActionResponse;

public final class Elasticsearch53TransportSingletons {

  private static final Instrumenter<ElasticTransportRequest, ActionResponse> INSTRUMENTER =
      ElasticsearchTransportInstrumenterFactory.create(
          "io.opentelemetry.elasticsearch-transport-5.3",
          new Elasticsearch53TransportExperimentalAttributesExtractor(),
          NetworkAttributesExtractor.create(new ElasticTransportNetworkAttributesGetter()));

  public static Instrumenter<ElasticTransportRequest, ActionResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private Elasticsearch53TransportSingletons() {}
}
