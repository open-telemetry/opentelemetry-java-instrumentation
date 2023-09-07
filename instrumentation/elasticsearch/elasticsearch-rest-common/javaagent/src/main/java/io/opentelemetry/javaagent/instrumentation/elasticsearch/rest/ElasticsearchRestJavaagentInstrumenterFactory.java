/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.elasticsearch.rest.internal.ElasticsearchRestInstrumenterFactory;
import io.opentelemetry.instrumentation.elasticsearch.rest.internal.ElasticsearchRestRequest;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.Collections;
import org.elasticsearch.client.Response;

public final class ElasticsearchRestJavaagentInstrumenterFactory {

  private static final boolean CAPTURE_SEARCH_QUERY =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.elasticsearch.capture-search-query", false);

  private ElasticsearchRestJavaagentInstrumenterFactory() {}

  public static Instrumenter<ElasticsearchRestRequest, Response> create(
      String instrumentationName) {
    return ElasticsearchRestInstrumenterFactory.create(
        GlobalOpenTelemetry.get(),
        instrumentationName,
        Collections.emptyList(),
        CommonConfig.get().getKnownHttpRequestMethods(),
        CAPTURE_SEARCH_QUERY);
  }
}
