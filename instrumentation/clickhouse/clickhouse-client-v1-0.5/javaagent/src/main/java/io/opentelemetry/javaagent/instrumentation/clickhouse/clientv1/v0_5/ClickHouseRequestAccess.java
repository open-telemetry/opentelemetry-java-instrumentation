/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import com.clickhouse.client.ClickHouseNode;
import com.clickhouse.client.ClickHouseNodes;
import com.clickhouse.client.ClickHouseRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

// Reflectively accesses package-private members of ClickHouseRequest so that this helper does not
// need to live in the com.clickhouse.client package.
public final class ClickHouseRequestAccess {

  @Nullable private static final Method GET_QUERY_METHOD = findGetQueryMethod();
  @Nullable private static final Field SERVER_FIELD = findServerField();

  @Nullable
  private static Method findGetQueryMethod() {
    try {
      Method method = ClickHouseRequest.class.getDeclaredMethod("getQuery");
      method.setAccessible(true);
      return method;
    } catch (Throwable t) {
      return null;
    }
  }

  @Nullable
  private static Field findServerField() {
    try {
      Field field = ClickHouseRequest.class.getDeclaredField("server");
      field.setAccessible(true);
      return field;
    } catch (Throwable t) {
      return null;
    }
  }

  public static String getQuery(ClickHouseRequest<?> clickHouseRequest) {
    if (GET_QUERY_METHOD == null) {
      return "";
    }
    try {
      Object result = GET_QUERY_METHOD.invoke(clickHouseRequest);
      return result instanceof String ? (String) result : "";
    } catch (Throwable t) {
      return "";
    }
  }

  @Nullable
  public static ClickHouseNodes getNodes(ClickHouseRequest<?> clickHouseRequest) {
    Object server = readServer(clickHouseRequest);
    return server instanceof ClickHouseNodes ? (ClickHouseNodes) server : null;
  }

  @Nullable
  public static ClickHouseNode getDirectNode(ClickHouseRequest<?> clickHouseRequest) {
    Object server = readServer(clickHouseRequest);
    return server instanceof ClickHouseNode ? (ClickHouseNode) server : null;
  }

  @Nullable
  private static Object readServer(ClickHouseRequest<?> clickHouseRequest) {
    if (SERVER_FIELD == null) {
      return null;
    }
    try {
      return SERVER_FIELD.get(clickHouseRequest);
    } catch (Throwable t) {
      return null;
    }
  }

  private ClickHouseRequestAccess() {}
}
