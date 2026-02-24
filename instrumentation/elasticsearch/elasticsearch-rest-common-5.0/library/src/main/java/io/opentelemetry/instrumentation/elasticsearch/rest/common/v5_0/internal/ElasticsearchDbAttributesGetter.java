/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.FINE;
import static java.util.stream.Collectors.joining;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.http.HttpEntity;
import org.elasticsearch.client.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class ElasticsearchDbAttributesGetter
    implements DbClientAttributesGetter<ElasticsearchRestRequest, Response> {

  private static final Logger logger =
      Logger.getLogger(ElasticsearchDbAttributesGetter.class.getName());

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ELASTICSEARCH = "elasticsearch";

  private final boolean captureSearchQuery;

  ElasticsearchDbAttributesGetter(boolean captureSearchQuery) {
    this.captureSearchQuery = captureSearchQuery;
  }

  @Override
  public String getDbSystemName(ElasticsearchRestRequest request) {
    return ELASTICSEARCH;
  }

  @Override
  @Nullable
  public String getDbNamespace(ElasticsearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(ElasticsearchRestRequest request) {
    ElasticsearchEndpointDefinition epDefinition = request.getEndpointDefinition();
    HttpEntity httpEntity = request.getHttpEntity();
    if (captureSearchQuery
        && epDefinition != null
        && epDefinition.isSearchEndpoint()
        && httpEntity != null
        && httpEntity.isRepeatable()) {
      // Retrieve HTTP body for search-type Elasticsearch requests when CAPTURE_SEARCH_QUERY is
      // enabled.
      try {
        return new BufferedReader(new InputStreamReader(httpEntity.getContent(), UTF_8))
            .lines()
            .collect(joining());
      } catch (IOException e) {
        logger.log(FINE, "Failed reading HTTP body content.", e);
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String getDbOperationName(ElasticsearchRestRequest request) {
    ElasticsearchEndpointDefinition endpointDefinition = request.getEndpointDefinition();
    return endpointDefinition != null ? endpointDefinition.getEndpointName() : null;
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(@Nullable Response response, @Nullable Throwable error) {
    if (response != null) {
      return Integer.toString(response.getStatusLine().getStatusCode());
    }
    return null;
  }

  @Nullable
  @Override
  public String getErrorType(
      ElasticsearchRestRequest request, @Nullable Response response, @Nullable Throwable error) {
    if (response != null) {
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode >= 400 || statusCode < 100) {
        return Integer.toString(statusCode);
      }
    }
    return null;
  }
}
