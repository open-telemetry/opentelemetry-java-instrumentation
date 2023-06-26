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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.elasticsearch.client.Response;

public class ElasticsearchClientAttributeExtractor
    implements AttributesExtractor<ElasticsearchRestRequest, Response> {
  private static final Pattern pathPartNamesPattern = Pattern.compile("\\{([^}]+)}");
  private final Map<String, EndpointPattern> endpointPatternMap = new ConcurrentHashMap<>();

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

  /**
   * Identifies the corresponding URL route by matching the actual URL path against valid URL routes
   * for that corresponding endpoint definition.
   *
   * <p>Once the correct URL route is found, this method retrieves the URL path parameters from the
   * URL path by using the corresponding URL route regex with embedded named capture groups for the
   * URL path parameters.
   */
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
        EndpointPattern endpointPattern = getEndpointPattern(route);
        Matcher matcher = endpointPattern.createMatcher(urlPath);
        if (matcher.find()) {
          for (String key : endpointPattern.getPathPartNames()) {
            try {
              String value = matcher.group(key);
              String attributeKey = "db.elasticsearch.path_parts." + key;
              internalSet(attributes, AttributeKey.stringKey(attributeKey), value);
            } catch (RuntimeException e) {
              // ignore
            }
          }

          routeFound = true;
        }
      }
    }
  }

  /**
   * For a given route creates, compiles and caches a regular expression pattern and retrieves a set
   * of pathPartNames (names of the URL path parameters).
   *
   * <p>The regex pattern is later being used to match against a URL path to retrieve the URL path
   * parameters for that route pattern using named regex capture groups.
   *
   * @param route The route to create the regex pattern for.
   * @return The EndpointPattern containing the regex pattern and the URL path parameter names/keys.
   */
  private EndpointPattern getEndpointPattern(String route) {
    EndpointPattern endpointPattern = endpointPatternMap.get(route);
    if (endpointPattern == null) {
      synchronized (this) {
        if (!endpointPatternMap.containsKey(route)) {
          String regexStr = '^' + route.replace("{", "(?<").replace("}", ">[^/]+)") + '$';
          Pattern regexPattern = Pattern.compile(regexStr);

          List<String> pathPartNames;
          if (route.contains("{")) {
            pathPartNames = new ArrayList<>();
            Matcher matcher = pathPartNamesPattern.matcher(route);
            while (matcher.find()) {
              try {
                pathPartNames.add(matcher.group(1));
              } catch (RuntimeException e) {
                // ignore
              }
            }
          } else {
            pathPartNames = Collections.emptyList();
          }
          endpointPattern = new EndpointPattern(regexPattern, pathPartNames);
          endpointPatternMap.put(route, endpointPattern);
        }
      }
    }
    return endpointPattern;
  }

  private static final class EndpointPattern {
    private final Pattern pattern;
    private final List<String> pathPartNames;

    public EndpointPattern(Pattern pattern, List<String> pathPartNames) {
      this.pattern = pattern;
      this.pathPartNames = pathPartNames;
    }

    public Matcher createMatcher(String urlPath) {
      return pattern.matcher(urlPath);
    }

    public List<String> getPathPartNames() {
      return pathPartNames;
    }
  }
}
