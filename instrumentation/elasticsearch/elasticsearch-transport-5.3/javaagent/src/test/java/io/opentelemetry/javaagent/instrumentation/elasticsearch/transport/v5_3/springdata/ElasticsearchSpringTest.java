/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata;

import static io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchOperationNames.operationName;

abstract class ElasticsearchSpringTest {
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.elasticsearch.experimental-span-attributes");

  protected static final String CLUSTER_HEALTH_OPERATION =
      operationName("ClusterHealthAction", "cluster:monitor/health");
  protected static final String CREATE_INDEX_OPERATION =
      operationName("CreateIndexAction", "indices:admin/create");
  protected static final String DELETE_OPERATION =
      operationName("DeleteAction", "indices:data/write/delete");
  protected static final String GET_OPERATION = operationName("GetAction", "indices:data/read/get");
  protected static final String INDEX_OPERATION =
      operationName("IndexAction", "indices:data/write/index");
  protected static final String REFRESH_OPERATION =
      operationName("RefreshAction", "indices:admin/refresh");
  protected static final String SEARCH_OPERATION =
      operationName("SearchAction", "indices:data/read/search");

  protected static String experimental(String value) {
    if (!EXPERIMENTAL_ATTRIBUTES) {
      return null;
    }
    return value;
  }

  protected static Long experimental(long value) {
    if (!EXPERIMENTAL_ATTRIBUTES) {
      return null;
    }
    return value;
  }
}
