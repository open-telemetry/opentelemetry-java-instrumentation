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
      // Retrieve HTTP body for search-type Elasticsearch requests when captureSearchQuery is
      // enabled.
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(httpEntity.getContent(), UTF_8))) {
        return reader.lines().collect(joining());
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
    if (endpointDefinition != null) {
      return endpointDefinition.getEndpointName();
    }
    return inferOperationName(request.getMethod(), request.getEndpoint());
  }

  @Deprecated // to be removed in 3.0
  @Override
  @Nullable
  public String getDbOperation(ElasticsearchRestRequest request) {
    ElasticsearchEndpointDefinition endpointDefinition = request.getEndpointDefinition();
    return endpointDefinition != null ? endpointDefinition.getEndpointName() : null;
  }

  @Nullable
  static String inferOperationName(String method, String endpoint) {
    int end = endpoint.indexOf('?');
    if (end < 0) {
      end = endpoint.length();
    }

    int start = 0;
    if (start < end && endpoint.charAt(start) == '/') {
      // low-level REST client endpoints conventionally start with a single leading slash
      start++;
    }

    // Only the first three path segments are needed to infer the operation name, so extract them
    // in a single pass instead of allocating an array for the whole path on this hot path.
    String segment0 = null;
    String segment1 = null;
    String segment2 = null;
    int segmentStart = start;
    int found = 0;
    for (int i = start; i <= end && found < 3; i++) {
      if (i == end || endpoint.charAt(i) == '/') {
        String segment = endpoint.substring(segmentStart, i);
        switch (found++) {
          case 0:
            segment0 = segment;
            break;
          case 1:
            segment1 = segment;
            break;
          default:
            segment2 = segment;
            break;
        }
        segmentStart = i + 1;
      }
    }

    if (segment0 == null || segment0.isEmpty()) {
      return null;
    }
    if (segment0.startsWith("_")) {
      return inferOperationNameFromApiSegments(method, segment0, segment1);
    }
    if (segment1 != null && segment1.startsWith("_")) {
      return inferOperationNameFromApiSegments(method, segment1, segment2);
    }
    return null;
  }

  @Nullable
  private static String inferOperationNameFromApiSegments(
      String method, String apiSegmentRaw, @Nullable String nextSegment) {
    String apiSegment = stripLeadingUnderscores(apiSegmentRaw);
    if (apiSegment.isEmpty()) {
      return null;
    }
    String documentOperation = inferDocumentOperationName(method, apiSegment);
    if (documentOperation != null) {
      return documentOperation;
    }
    if (isGroupedApi(apiSegment) && nextSegment != null && !nextSegment.startsWith("_")) {
      return apiSegment + "." + nextSegment;
    }
    return apiSegment;
  }

  @Nullable
  private static String inferDocumentOperationName(String method, String apiSegment) {
    switch (apiSegment) {
      case "create":
      case "update":
        return apiSegment;
      case "doc":
        return inferDocOperationName(method);
      default:
        return null;
    }
  }

  @Nullable
  private static String inferDocOperationName(String method) {
    switch (method) {
      case "DELETE":
        return "delete";
      case "GET":
        return "get";
      case "POST":
      case "PUT":
        return "index";
      default:
        return null;
    }
  }

  private static boolean isGroupedApi(String apiSegment) {
    switch (apiSegment) {
      case "cat":
      case "cluster":
      case "nodes":
      case "snapshot":
      case "tasks":
        return true;
      default:
        return false;
    }
  }

  private static String stripLeadingUnderscores(String value) {
    while (value.startsWith("_")) {
      value = value.substring(1);
    }
    return value;
  }

  @Override
  @Nullable
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
