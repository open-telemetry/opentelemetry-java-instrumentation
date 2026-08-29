/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.v7_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchRestRequest;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

/** Entrypoint for instrumenting Apache Elasticsearch Rest clients. */
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
   * Constructs a new tracing-enabled {@link RestClient} using the provided {@link
   * RestClientBuilder}.
   *
   * <p>This overload captures the configured nodes when it builds the client, before sniffing or
   * node updates can replace them.
   */
  public RestClient wrap(RestClientBuilder restClientBuilder) {
    return RestClientWrapper.wrap(restClientBuilder, instrumenter);
  }

  /**
   * Constructs a new tracing-enabled {@link RestClient} using the provided {@link RestClient}
   * instance.
   *
   * <p>This overload cannot capture the original configured nodes because a constructed client
   * exposes only its current routing nodes. Use {@link #wrap(RestClientBuilder)} to capture the
   * configured target.
   */
  public RestClient wrap(RestClient restClient) {
    return RestClientWrapper.wrap(restClient, instrumenter);
  }

  /**
   * Constructs a new tracing-enabled {@link RestClient} using the provided {@link RestClient}
   * instance and its original configured hosts.
   *
   * <p>Use this overload when the client must be built elsewhere but its original configured hosts
   * are still available. The hosts are captured when this method is called and are not affected by
   * later node updates.
   */
  public RestClient wrap(RestClient restClient, HttpHost... configuredHosts) {
    return RestClientWrapper.wrap(restClient, instrumenter, configuredHosts);
  }
}
