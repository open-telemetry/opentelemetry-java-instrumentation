/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A focused route table mapping an OpenSearch REST request (HTTP method and path) to an operation
 * name such as {@code index}, {@code search} or {@code cluster.health}, plus the path parameters
 * that name identifies.
 *
 * <p>OpenSearch forked Elasticsearch 7.10, so the path templates match those the Elasticsearch
 * instrumentation uses in {@code ElasticsearchEndpointMap}. Unlike Elasticsearch, an OpenSearch
 * REST request carries no endpoint id, so the operation has to be derived from the raw path here.
 * This table covers the common OpenSearch REST endpoints rather than every route; an unmatched
 * request yields {@code null} and the caller falls back to the HTTP method.
 *
 * <p>Within each HTTP method the routes are ordered from most specific to most general, because the
 * first matching route wins. This ordering is what lets {@code /{index}/_doc/{id}} be told apart
 * from the two-level structural path {@code /{index}/_doc}.
 */
final class OpenSearchEndpointMap {

  private static final String MASKED_VALUE = "?";

  private static final Map<String, List<OpenSearchEndpointRoute>> ROUTES_BY_METHOD = buildRoutes();

  @Nullable
  static String getOperationName(String method, String endpoint) {
    OpenSearchEndpointRoute route = findRoute(method, endpoint);
    return route != null ? route.getOperationName() : null;
  }

  /** Exposes every registered route so a test can assert invariants across the whole table. */
  static Map<String, List<OpenSearchEndpointRoute>> getRoutesByMethod() {
    return Collections.unmodifiableMap(ROUTES_BY_METHOD);
  }

  /**
   * Masks the path parameters of the matching route, so {@code my-index/_doc/12345} becomes {@code
   * my-index/_doc/?} while index names stay intact. The {@code endpoint} must be a path with no
   * query string; the caller masks any query separately. Returns {@code null} when no route
   * matches.
   */
  @Nullable
  static String maskPathParameters(String method, String endpoint) {
    boolean hadLeadingSlash = endpoint.startsWith("/");
    String normalizedPath = hadLeadingSlash ? endpoint : "/" + endpoint;

    OpenSearchEndpointRoute route = findRoute(method, normalizedPath);
    if (route == null) {
      return null;
    }
    String maskedPath = route.maskPathParameters(normalizedPath, MASKED_VALUE);
    if (maskedPath == null) {
      return null;
    }
    if (!hadLeadingSlash) {
      maskedPath = maskedPath.substring(1);
    }
    return maskedPath;
  }

  @Nullable
  private static OpenSearchEndpointRoute findRoute(String method, String endpoint) {
    List<OpenSearchEndpointRoute> routes = ROUTES_BY_METHOD.get(method);
    if (routes == null) {
      return null;
    }
    String path = pathOnly(endpoint);
    for (OpenSearchEndpointRoute route : routes) {
      if (route.matches(path)) {
        return route;
      }
    }
    return null;
  }

  private static String pathOnly(String endpoint) {
    int queryIdx = endpoint.indexOf('?');
    String path = queryIdx >= 0 ? endpoint.substring(0, queryIdx) : endpoint;
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return path;
  }

  private static Map<String, List<OpenSearchEndpointRoute>> buildRoutes() {
    Map<String, List<OpenSearchEndpointRoute>> map = new HashMap<>();

    // single-document routes
    put(map, "PUT", "create", "/{index}/_create/{id}");
    put(map, "POST", "create", "/{index}/_create/{id}");
    put(map, "PUT", "index", "/{index}/_doc/{id}");
    put(map, "POST", "index", "/{index}/_doc/{id}", "/{index}/_doc");
    put(map, "POST", "update", "/{index}/_update/{id}");
    put(map, "DELETE", "delete", "/{index}/_doc/{id}");
    put(map, "GET", "get", "/{index}/_doc/{id}");
    put(map, "HEAD", "exists", "/{index}/_doc/{id}");
    put(map, "GET", "get_source", "/{index}/_source/{id}");
    put(map, "HEAD", "exists_source", "/{index}/_source/{id}");
    put(map, "GET", "explain", "/{index}/_explain/{id}");
    put(map, "POST", "explain", "/{index}/_explain/{id}");
    put(map, "GET", "termvectors", "/{index}/_termvectors/{id}");
    put(map, "POST", "termvectors", "/{index}/_termvectors/{id}", "/{index}/_termvectors");

    // bulk and multi-document routes
    put(map, "POST", "bulk", "/_bulk", "/{index}/_bulk");
    put(map, "PUT", "bulk", "/_bulk", "/{index}/_bulk");
    put(map, "GET", "mget", "/_mget", "/{index}/_mget");
    put(map, "POST", "mget", "/_mget", "/{index}/_mget");
    put(map, "GET", "msearch", "/_msearch", "/{index}/_msearch");
    put(map, "POST", "msearch", "/_msearch", "/{index}/_msearch");

    // search and count
    put(map, "GET", "search", "/_search", "/{index}/_search");
    put(map, "POST", "search", "/_search", "/{index}/_search");
    put(map, "GET", "count", "/_count", "/{index}/_count");
    put(map, "POST", "count", "/_count", "/{index}/_count");
    put(map, "GET", "scroll", "/_search/scroll", "/_search/scroll/{scroll_id}");
    put(map, "POST", "scroll", "/_search/scroll", "/_search/scroll/{scroll_id}");
    put(map, "DELETE", "clear_scroll", "/_search/scroll", "/_search/scroll/{scroll_id}");
    put(map, "GET", "field_caps", "/_field_caps", "/{index}/_field_caps");
    put(map, "POST", "field_caps", "/_field_caps", "/{index}/_field_caps");

    // index management
    put(map, "PUT", "indices.create", "/{index}");
    put(map, "DELETE", "indices.delete", "/{index}");
    put(map, "GET", "indices.get", "/{index}");
    put(map, "HEAD", "indices.exists", "/{index}");
    put(map, "POST", "indices.refresh", "/_refresh", "/{index}/_refresh");
    put(map, "GET", "indices.refresh", "/_refresh", "/{index}/_refresh");
    put(map, "POST", "indices.flush", "/_flush", "/{index}/_flush");
    put(map, "GET", "indices.flush", "/_flush", "/{index}/_flush");
    put(map, "POST", "indices.open", "/{index}/_open");
    put(map, "POST", "indices.close", "/{index}/_close");

    // mapping, settings and alias management
    put(map, "PUT", "indices.put_mapping", "/{index}/_mapping");
    put(map, "GET", "indices.get_mapping", "/_mapping", "/{index}/_mapping");
    put(map, "PUT", "indices.put_settings", "/_settings", "/{index}/_settings");
    put(map, "GET", "indices.get_settings", "/_settings", "/{index}/_settings");
    put(
        map,
        "GET",
        "indices.get_alias",
        "/_alias",
        "/_alias/{name}",
        "/{index}/_alias",
        "/{index}/_alias/{name}");
    put(map, "PUT", "indices.put_alias", "/{index}/_alias/{name}", "/{index}/_aliases/{name}");
    put(map, "POST", "indices.put_alias", "/{index}/_alias/{name}", "/{index}/_aliases/{name}");
    put(
        map,
        "DELETE",
        "indices.delete_alias",
        "/{index}/_alias/{name}",
        "/{index}/_aliases/{name}");
    put(map, "POST", "indices.update_aliases", "/_aliases");
    put(map, "GET", "indices.analyze", "/_analyze", "/{index}/_analyze");
    put(map, "POST", "indices.analyze", "/_analyze", "/{index}/_analyze");

    // cluster and node health
    put(map, "GET", "cluster.health", "/_cluster/health", "/_cluster/health/{index}");
    put(
        map,
        "GET",
        "cluster.state",
        "/_cluster/state",
        "/_cluster/state/{metric}",
        "/_cluster/state/{metric}/{index}");
    put(map, "GET", "cluster.stats", "/_cluster/stats", "/_cluster/stats/nodes/{node_id}");
    put(map, "GET", "cluster.get_settings", "/_cluster/settings");
    put(map, "PUT", "cluster.put_settings", "/_cluster/settings");
    put(
        map,
        "GET",
        "nodes.stats",
        "/_nodes/stats",
        "/_nodes/{node_id}/stats",
        "/_nodes/stats/{metric}",
        "/_nodes/{node_id}/stats/{metric}",
        "/_nodes/stats/{metric}/{index_metric}",
        "/_nodes/{node_id}/stats/{metric}/{index_metric}");
    put(
        map,
        "GET",
        "nodes.info",
        "/_nodes",
        "/_nodes/{node_info_metric}",
        "/_nodes/{node_id}",
        "/_nodes/{node_id}/{metric}");

    // cat API
    put(map, "GET", "cat.indices", "/_cat/indices", "/_cat/indices/{index}");
    put(map, "GET", "cat.health", "/_cat/health");
    put(map, "GET", "cat.nodes", "/_cat/nodes");
    put(map, "GET", "cat.shards", "/_cat/shards", "/_cat/shards/{index}");
    put(map, "GET", "cat.aliases", "/_cat/aliases", "/_cat/aliases/{name}");

    // top-level info and ping
    put(map, "GET", "info", "/");
    put(map, "HEAD", "ping", "/");

    // legacy typed document routes (/{index}/{type}/{id}); listed last so keyword routes such as
    // /{index}/_doc/{id} match first. The type is treated as endpoint structure, the id is masked.
    put(map, "PUT", "create", "/{index}/{type}/{id}/_create");
    put(map, "POST", "create", "/{index}/{type}/{id}/_create");
    put(map, "POST", "update", "/{index}/{type}/{id}/_update");
    put(map, "GET", "get_source", "/{index}/{type}/{id}/_source");
    put(map, "HEAD", "exists_source", "/{index}/{type}/{id}/_source");
    put(map, "GET", "explain", "/{index}/{type}/{id}/_explain");
    put(map, "POST", "explain", "/{index}/{type}/{id}/_explain");
    put(
        map,
        "GET",
        "termvectors",
        "/{index}/{type}/{id}/_termvectors",
        "/{index}/{type}/_termvectors");
    put(
        map,
        "POST",
        "termvectors",
        "/{index}/{type}/{id}/_termvectors",
        "/{index}/{type}/_termvectors");
    put(map, "PUT", "index", "/{index}/{type}/{id}");
    put(map, "POST", "index", "/{index}/{type}/{id}", "/{index}/{type}");
    put(map, "GET", "get", "/{index}/{type}/{id}");
    put(map, "DELETE", "delete", "/{index}/{type}/{id}");
    put(map, "HEAD", "exists", "/{index}/{type}/{id}");

    for (Map.Entry<String, List<OpenSearchEndpointRoute>> entry : map.entrySet()) {
      entry.setValue(Collections.unmodifiableList(entry.getValue()));
    }
    return Collections.unmodifiableMap(map);
  }

  private static void put(
      Map<String, List<OpenSearchEndpointRoute>> map,
      String method,
      String operationName,
      String... templates) {
    List<OpenSearchEndpointRoute> routes = map.computeIfAbsent(method, unused -> new ArrayList<>());
    for (String template : templates) {
      routes.add(new OpenSearchEndpointRoute(operationName, template));
    }
  }

  private OpenSearchEndpointMap() {}
}
