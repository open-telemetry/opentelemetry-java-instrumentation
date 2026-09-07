/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.clickhouse.client;

// helper class for accessing package private members in com.clickhouse.client package
public class ClickHouseRequestAccess {

  public static String getQuery(ClickHouseRequest<?> clickHouseRequest) {
    return clickHouseRequest.getQuery();
  }

  public static ClickHouseNodes getNodes(ClickHouseRequest<?> clickHouseRequest) {
    return clickHouseRequest.server instanceof ClickHouseNodes
        ? (ClickHouseNodes) clickHouseRequest.server
        : null;
  }

  public static ClickHouseNode getDirectNode(ClickHouseRequest<?> clickHouseRequest) {
    return clickHouseRequest.server instanceof ClickHouseNode
        ? (ClickHouseNode) clickHouseRequest.server
        : null;
  }

  private ClickHouseRequestAccess() {}
}
