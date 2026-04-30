/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static io.opentelemetry.instrumentation.api.internal.HttpConstants._OTHER;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class ElasticsearchClientAttributeExtractor
    implements AttributesExtractor<ElasticsearchRestRequest, Response> {

  private static final String PATH_PARTS_ATTRIBUTE_PREFIX = "db.elasticsearch.path_parts.";

  // copied from HttpAttributes
  private static final AttributeKey<String> HTTP_REQUEST_METHOD =
      AttributeKey.stringKey("http.request.method");
  // copied from HttpAttributes
  private static final AttributeKey<String> HTTP_REQUEST_METHOD_ORIGINAL =
      AttributeKey.stringKey("http.request.method_original");
  // copied from ServerAttributes
  private static final AttributeKey<String> SERVER_ADDRESS =
      AttributeKey.stringKey("server.address");
  // copied from ServerAttributes
  private static final AttributeKey<Long> SERVER_PORT = AttributeKey.longKey("server.port");
  // copied from UrlAttributes
  private static final AttributeKey<String> URL_FULL = AttributeKey.stringKey("url.full");

  private static final Cache<String, AttributeKey<String>> pathPartKeysCache = Cache.bounded(64);

  private final Set<String> knownMethods;

  ElasticsearchClientAttributeExtractor(Set<String> knownMethods) {
    this.knownMethods = new HashSet<>(knownMethods);
  }

  private static void setServerAttributes(AttributesBuilder attributes, Response response) {
    HttpHost host = response.getHost();
    if (host != null) {
      attributes.put(SERVER_ADDRESS, host.getHostName());
      attributes.put(SERVER_PORT, (long) host.getPort());
    }
  }

  private static void setUrlAttribute(AttributesBuilder attributes, Response response) {
    String uri = response.getRequestLine().getUri();
    uri = uri.startsWith("/") ? uri : "/" + uri;
    String fullUrl = response.getHost().toURI() + uri;

    attributes.put(URL_FULL, fullUrl);
  }

  private static void setPathPartsAttributes(
      AttributesBuilder attributes, ElasticsearchRestRequest request) {
    ElasticsearchEndpointDefinition endpointDef = request.getEndpointDefinition();
    if (endpointDef == null) {
      return;
    }

    endpointDef.processPathParts(
        request.getEndpoint(),
        (key, value) -> {
          AttributeKey<String> attributeKey =
              pathPartKeysCache.computeIfAbsent(
                  key, k -> AttributeKey.stringKey(PATH_PARTS_ATTRIBUTE_PREFIX + k));
          attributes.put(attributeKey, value);
        });
  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ElasticsearchRestRequest request) {
    String method = request.getMethod();
    if (method == null || knownMethods.contains(method)) {
      attributes.put(HTTP_REQUEST_METHOD, method);
    } else {
      attributes.put(HTTP_REQUEST_METHOD, _OTHER);
      attributes.put(HTTP_REQUEST_METHOD_ORIGINAL, method);
    }
    setPathPartsAttributes(attributes, request);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ElasticsearchRestRequest request,
      @Nullable Response response,
      @Nullable Throwable error) {

    if (response != null) {
      setUrlAttribute(attributes, response);
      setServerAttributes(attributes, response);
    }
  }
}
