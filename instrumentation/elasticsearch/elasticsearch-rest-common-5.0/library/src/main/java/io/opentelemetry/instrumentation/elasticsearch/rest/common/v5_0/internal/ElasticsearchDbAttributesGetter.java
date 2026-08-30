/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.FINE;
import static java.util.stream.Collectors.joining;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.function.UnaryOperator;
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
  @Nullable private final UnaryOperator<String> sanitizer;

  ElasticsearchDbAttributesGetter(
      boolean captureSearchQuery, @Nullable UnaryOperator<String> sanitizer) {
    this.captureSearchQuery = captureSearchQuery;
    this.sanitizer = sanitizer;
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
        && isSearchEndpoint(request.getEndpoint(), epDefinition)
        && httpEntity != null
        && httpEntity.isRepeatable()) {
      // Retrieve HTTP body for search-type Elasticsearch requests when captureSearchQuery is
      // enabled.
      String body = readBody(httpEntity);
      if (body == null) {
        return null;
      }
      if (sanitizer == null) {
        // sanitization was explicitly disabled, so capture the body verbatim
        return body;
      }
      // the sanitizer returns null when the body cannot be sanitized (malformed, non-JSON, or no
      // sanitizer registered), in which case the body is dropped rather than captured raw
      return sanitizer.apply(body);
    }
    return null;
  }

  private static boolean isSearchEndpoint(
      String endpoint, @Nullable ElasticsearchEndpointDefinition endpointDefinition) {
    if (endpointDefinition != null) {
      return endpointDefinition.isSearchEndpoint();
    }

    int queryStart = endpoint.indexOf('?');
    String path = queryStart == -1 ? endpoint : endpoint.substring(0, queryStart);
    int pathStart = path.startsWith("/") ? 1 : 0;
    String[] segments = path.substring(pathStart).split("/", -1);
    for (String segment : segments) {
      if (segment.isEmpty()) {
        return false;
      }
    }

    if (segments.length == 1) {
      return isSearchAction(segments[0]);
    }
    if (segments.length == 2) {
      return isSearchAction(segments[1])
          || segments[1].equals("_terms_enum")
          || ((segments[0].equals("_search") || segments[0].equals("_msearch"))
              && segments[1].equals("template"))
          || (segments[0].equals("_render") && segments[1].equals("template"));
    }
    if (segments.length == 3) {
      return segments[2].equals("_search")
          || ((segments[1].equals("_search") || segments[1].equals("_msearch"))
              && segments[2].equals("template"))
          || (segments[0].equals("_render") && segments[1].equals("template"))
          || (segments[1].equals("_eql") && segments[2].equals("search"));
    }
    return false;
  }

  private static boolean isSearchAction(String segment) {
    return segment.equals("_search")
        || segment.equals("_msearch")
        || segment.equals("_async_search");
  }

  @Nullable
  private static String readBody(HttpEntity httpEntity) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(httpEntity.getContent(), UTF_8))) {
      return reader.lines().collect(joining());
    } catch (IOException | UncheckedIOException e) {
      logger.log(FINE, "Failed reading HTTP body content.", e);
    }
    return null;
  }

  @Override
  @Nullable
  public String getDbOperationName(ElasticsearchRestRequest request) {
    ElasticsearchEndpointDefinition endpointDefinition = request.getEndpointDefinition();
    return endpointDefinition != null ? endpointDefinition.getEndpointName() : null;
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

  @Override
  @Nullable
  public String getNetworkPeerAddress(
      ElasticsearchRestRequest request, @Nullable Response response) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    InetSocketAddress peerAddress = request.getPeerState().getPeerAddress();
    return peerAddress != null ? peerAddress.getAddress().getHostAddress() : null;
  }

  @Override
  @Nullable
  public Integer getNetworkPeerPort(ElasticsearchRestRequest request, @Nullable Response response) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    InetSocketAddress peerAddress = request.getPeerState().getPeerAddress();
    return peerAddress != null ? peerAddress.getPort() : null;
  }

  @Override
  @Nullable
  public String getServerAddress(ElasticsearchRestRequest request) {

    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    ElasticsearchServerTarget target = request.getServerTarget();
    return target != null ? target.getAddress() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(ElasticsearchRestRequest request) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    ElasticsearchServerTarget target = request.getServerTarget();

    return target != null ? target.getPort() : null;
  }
}
