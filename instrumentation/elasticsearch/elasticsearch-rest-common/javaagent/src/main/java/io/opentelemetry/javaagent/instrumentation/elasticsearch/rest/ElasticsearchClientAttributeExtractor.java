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
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.Inet6Address;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Response;

public class ElasticsearchClientAttributeExtractor
    implements AttributesExtractor<ElasticsearchRestRequest, Response> {
  private static final Pattern pathPartNamesPattern = Pattern.compile("\\{([^}]+)}");
  private final Map<String, Pattern> regexPatternMap = new ConcurrentHashMap<>();
  private final Map<String, List<String>> routePathPartNames = new ConcurrentHashMap<>();

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
      HttpHost host = response.getHost();
      String uri = response.getRequestLine().getUri();
      uri = uri.startsWith("/") ? uri : "/" + uri;
      String url = host.toURI() + uri;

      internalSet(attributes, SemanticAttributes.HTTP_URL, url);

      if (host.getAddress() != null) {
        internalSet(
            attributes, SemanticAttributes.NET_PEER_NAME, host.getAddress().getHostAddress());
        internalSet(attributes, SemanticAttributes.NET_PEER_PORT, (long) host.getPort());
        if (host.getAddress() instanceof Inet6Address) {
          internalSet(attributes, SemanticAttributes.NET_SOCK_FAMILY, "inet6");
        }
      }
    }
  }

  private void setPathPartsAttributes(
      AttributesBuilder attributes, ElasticsearchRestRequest request) {
    ElasticsearchEndpointDefinition endpointDef = request.getEndpointDefinition();
    if (endpointDef == null) {
      return;
    }
    String[] availableRoutes = endpointDef.getRoutes();
    if (availableRoutes == null || availableRoutes.length == 0) {
      return;
    }
    String urlPath = request.getEndpoint();
    boolean routeFound = false;
    for (int i = 0; i < availableRoutes.length && !routeFound; i++) {
      String route = availableRoutes[i];
      if (route.contains("{")) {
        Matcher matcher = matchUrl(route, urlPath);
        if (matcher.find()) {
          List<String> pathPartNames = routePathPartNames.get(route);
          if (pathPartNames != null) {
            for (String key : pathPartNames) {
              try {
                String value = matcher.group(key);
                String attributeKey = "db.elasticsearch.path_parts." + key;
                internalSet(attributes, AttributeKey.stringKey(attributeKey), value);
              } catch (RuntimeException e) {
                // ignore
              }
            }
          }

          routeFound = true;
        }
      }
    }
  }

  private Matcher matchUrl(String route, String urlPath) {
    if (!regexPatternMap.containsKey(route)) {

      String regexStr = '^' + route.replace("{", "(?<").replace("}", ">[^/]+)") + '$';
      regexPatternMap.put(route, Pattern.compile(regexStr));

      if (route.contains("{")) {
        List<String> pathPartNames = new ArrayList<>();
        Matcher matcher = pathPartNamesPattern.matcher(route);
        while (matcher.find()) {
          try {
            pathPartNames.add(matcher.group(1));
          } catch (RuntimeException e) {
            // ignore
          }
        }
        routePathPartNames.put(route, pathPartNames);
      }
    }

    Pattern pattern = regexPatternMap.get(route);
    return pattern.matcher(urlPath);
  }
}
