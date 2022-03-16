/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v6_4;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchRestInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchRestRequest;
import org.elasticsearch.client.Response;

public final class ElasticsearchRest6Singletons {

  private static final Instrumenter<ElasticsearchRestRequest, Response> INSTRUMENTER =
      ElasticsearchRestInstrumenterFactory.create("io.opentelemetry.elasticsearch-rest-6.4");

  public static Instrumenter<ElasticsearchRestRequest, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private ElasticsearchRest6Singletons() {}
}
