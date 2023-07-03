/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetAddress;
import javax.annotation.Nullable;
import org.elasticsearch.client.Response;

public class ElasticsearchClientAttributeExtractor
    implements AttributesExtractor<ElasticsearchRestRequest, Response> {

  private static final String PATH_PARTS_ATTRIBUTE_PREFIX = "db.elasticsearch.path_parts.";

  private static void setServerAttributes(AttributesBuilder attributes, Response response) {
    InetAddress hostAddress = response.getHost().getAddress();
    if (hostAddress != null) {
      if (SemconvStability.emitStableHttpSemconv()) {
        internalSet(attributes, NetworkAttributes.SERVER_ADDRESS, hostAddress.getHostAddress());
        internalSet(attributes, NetworkAttributes.SERVER_PORT, (long) response.getHost().getPort());
      }
      if (SemconvStability.emitOldHttpSemconv()) {
        internalSet(attributes, SemanticAttributes.NET_PEER_NAME, hostAddress.getHostAddress());
        internalSet(
            attributes, SemanticAttributes.NET_PEER_PORT, (long) response.getHost().getPort());
      }
    }
  }

  private static void setUrlAttribute(AttributesBuilder attributes, Response response) {
    String uri = response.getRequestLine().getUri();
    uri = uri.startsWith("/") ? uri : "/" + uri;
    String fullUrl = response.getHost().toURI() + uri;

    if (SemconvStability.emitStableHttpSemconv()) {
      internalSet(attributes, UrlAttributes.URL_FULL, fullUrl);
    }

    if (SemconvStability.emitOldHttpSemconv()) {
      internalSet(attributes, SemanticAttributes.HTTP_URL, fullUrl);
    }
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
          String attributeKey = PATH_PARTS_ATTRIBUTE_PREFIX + key;
          internalSet(attributes, AttributeKey.stringKey(attributeKey), value);
        });
  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ElasticsearchRestRequest request) {
    internalSet(attributes, SemanticAttributes.HTTP_METHOD, request.getMethod());
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
