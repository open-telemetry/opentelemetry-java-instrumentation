/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.ElasticsearchTransportInstrumenterFactory;
import org.elasticsearch.action.ActionResponse;

final class Elasticsearch6TransportSingletons {

  private static final Instrumenter<ElasticTransportRequest, ActionResponse> instrumenter =
      ElasticsearchTransportInstrumenterFactory.create(
          "io.opentelemetry.elasticsearch-transport-6.0",
          new Elasticsearch6TransportExperimentalAttributesExtractor(),
          new Elasticsearch6TransportAttributesGetter());

  public static Instrumenter<ElasticTransportRequest, ActionResponse> instrumenter() {
    return instrumenter;
  }

  private Elasticsearch6TransportSingletons() {}
}
