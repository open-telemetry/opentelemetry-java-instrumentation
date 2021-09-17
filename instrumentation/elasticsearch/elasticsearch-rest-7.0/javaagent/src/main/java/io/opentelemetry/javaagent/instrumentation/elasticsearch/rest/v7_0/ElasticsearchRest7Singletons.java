/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v7_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchRestInstrumenterFactory;
import org.elasticsearch.client.Response;

public final class ElasticsearchRest7Singletons {

  private static final Instrumenter<String, Response> INSTRUMENTER =
      ElasticsearchRestInstrumenterFactory.create("io.opentelemetry.elasticsearch-rest-7.0");

  public static Instrumenter<String, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private ElasticsearchRest7Singletons() {}
}
