/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportNetResponseAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticsearchTransportInstrumenterFactory;
import org.elasticsearch.action.ActionResponse;

public final class Elasticsearch5TransportSingletons {

  private static final Instrumenter<ElasticTransportRequest, ActionResponse> INSTRUMENTER =
      ElasticsearchTransportInstrumenterFactory.create(
          "io.opentelemetry.elasticsearch-transport-5.0",
          new Elasticsearch5TransportExperimentalAttributesExtractor(),
          new NetClientAttributesExtractor<>(new ElasticTransportNetResponseAttributesExtractor()));

  public static Instrumenter<ElasticTransportRequest, ActionResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private Elasticsearch5TransportSingletons() {}
}
