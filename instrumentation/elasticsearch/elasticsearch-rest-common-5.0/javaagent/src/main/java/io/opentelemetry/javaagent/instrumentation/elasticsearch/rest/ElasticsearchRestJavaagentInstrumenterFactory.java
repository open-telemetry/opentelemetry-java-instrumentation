/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchRestInstrumenterFactory;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchRestRequest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.elasticsearch.client.Response;

public final class ElasticsearchRestJavaagentInstrumenterFactory {

  private static final boolean CAPTURE_SEARCH_QUERY =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "elasticsearch", "capture_search_query")
          .orElse(false);

  private ElasticsearchRestJavaagentInstrumenterFactory() {}

  public static Instrumenter<ElasticsearchRestRequest, Response> create(
      String instrumentationName) {
    return ElasticsearchRestInstrumenterFactory.create(
        GlobalOpenTelemetry.get(),
        instrumentationName,
        Collections.emptyList(),
        Function.identity(),
        getKnownHttpMethods(),
        CAPTURE_SEARCH_QUERY);
  }

  private static Set<String> getKnownHttpMethods() {
    return DeclarativeConfigUtil.getList(
            GlobalOpenTelemetry.get(), "java", "http", "known_methods")
        .map(HashSet::new)
        .orElse(new HashSet<>(HttpConstants.KNOWN_METHODS));
  }
}
