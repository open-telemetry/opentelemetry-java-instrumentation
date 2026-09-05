/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.v7_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchRestRequest;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

/**
 * Entrypoint for instrumenting Apache Elasticsearch Rest clients.
 *
 * @deprecated The Elasticsearch REST library instrumentation is deprecated. Applications that can
 *     migrate should use the Elasticsearch Java API Client's native OpenTelemetry support,
 *     available in 7.17.20+ on the 7.x line and 8.10+. Direct REST-client users should use the
 *     javaagent instead. May be removed in the next minor release.
 */
@Deprecated // may be removed in the next minor release
@SuppressWarnings("deprecation")
public final class ElasticsearchRest7Telemetry {
  private final Instrumenter<ElasticsearchRestRequest, Response> instrumenter;

  /**
   * Returns a new {@link ElasticsearchRest7Telemetry} configured with the given {@link
   * OpenTelemetry}.
   */
  public static ElasticsearchRest7Telemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link ElasticsearchRest7TelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static ElasticsearchRest7TelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new ElasticsearchRest7TelemetryBuilder(openTelemetry);
  }

  ElasticsearchRest7Telemetry(Instrumenter<ElasticsearchRestRequest, Response> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /**
   * Construct a new tracing-enabled {@link RestClient} using the provided {@link RestClient}
   * instance.
   */
  public RestClient wrap(RestClient restClient) {
    return RestClientWrapper.wrap(restClient, instrumenter);
  }
}
