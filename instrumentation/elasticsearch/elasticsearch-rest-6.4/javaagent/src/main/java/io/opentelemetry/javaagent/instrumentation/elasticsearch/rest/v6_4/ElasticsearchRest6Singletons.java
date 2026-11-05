/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v6_4;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0.ElasticsearchRestInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0.ElasticsearchRestRequest;
import org.elasticsearch.client.Response;

class ElasticsearchRest6Singletons {

  private static final Instrumenter<ElasticsearchRestRequest, Response> instrumenter =
      ElasticsearchRestInstrumenterFactory.create("io.opentelemetry.elasticsearch-rest-6.4");

  static Instrumenter<ElasticsearchRestRequest, Response> instrumenter() {
    return instrumenter;
  }

  private ElasticsearchRest6Singletons() {}
}
