/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;

@AutoValue
public abstract class SpymemcachedRequest {

  private static final boolean SANITIZATION_ENABLED =
      DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "spymemcached");

  public static SpymemcachedRequest create(
      MemcachedConnection connection, String methodName, Object[] args) {
    String operationName = operationName(methodName);
    return new AutoValue_SpymemcachedRequest(
        connection,
        operationName,
        SpymemcachedQueryText.create(operationName, args, SANITIZATION_ENABLED));
  }

  public abstract MemcachedConnection getConnection();

  public abstract String getOperationName();

  public abstract String getQueryText();

  @Nullable private MemcachedNode handlingNode;
  @Nullable private InetSocketAddress handlingNodeAddress;
  private boolean hasMultipleHandlingNodes;

  public void setHandlingNode(@Nullable MemcachedNode node) {
    if (node == null || hasMultipleHandlingNodes) {
      return;
    }
    if (handlingNode != null && node != handlingNode) {
      // bulk operations may have multiple nodes, so if we see a different node than the one we
      // already have, we will not set any node for this request
      hasMultipleHandlingNodes = true;
      handlingNode = null;
      handlingNodeAddress = null;
      return;
    }

    handlingNode = node;
    SocketAddress socketAddress = node.getSocketAddress();
    if (socketAddress instanceof InetSocketAddress) {
      handlingNodeAddress = (InetSocketAddress) socketAddress;
    }
  }

  @Nullable
  public InetSocketAddress getHandlingNodeAddress() {
    return handlingNodeAddress;
  }

  private static String operationName(String methodName) {
    if (methodName.startsWith("async")) {
      methodName = methodName.substring("async".length());
    }
    if (methodName.startsWith("CAS")) {
      // 'CAS' name is special, we have to lowercase whole name
      return "cas" + methodName.substring("CAS".length());
    }

    char[] chars = methodName.toCharArray();
    // Lowercase first letter
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }
}
