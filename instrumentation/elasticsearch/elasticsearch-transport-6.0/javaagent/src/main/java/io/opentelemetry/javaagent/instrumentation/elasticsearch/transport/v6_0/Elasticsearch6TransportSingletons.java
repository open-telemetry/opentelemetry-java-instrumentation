/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticsearchTransportInstrumenterFactory;
import org.elasticsearch.action.ActionResponse;

public final class Elasticsearch6TransportSingletons {

  private static final Instrumenter<ElasticTransportRequest, ActionResponse> INSTRUMENTER =
      ElasticsearchTransportInstrumenterFactory.create(
          "io.opentelemetry.elasticsearch-transport-6.0",
          new Elasticsearch6TransportExperimentalAttributesExtractor(),
          new NetClientAttributesExtractor<>(new Elasticsearch6TransportNetAttributesExtractor()));

  public static Instrumenter<ElasticTransportRequest, ActionResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private Elasticsearch6TransportSingletons() {}
}
