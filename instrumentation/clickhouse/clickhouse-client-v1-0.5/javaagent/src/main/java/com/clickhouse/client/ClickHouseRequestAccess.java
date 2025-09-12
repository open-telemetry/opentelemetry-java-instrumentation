/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.clickhouse.client;

// helper class for accessing package private members in com.clickhouse.client package
public final class ClickHouseRequestAccess {

  public static String getQuery(ClickHouseRequest<?> clickHouseRequest) {
    return clickHouseRequest.getQuery();
  }

  private ClickHouseRequestAccess() {}
}
