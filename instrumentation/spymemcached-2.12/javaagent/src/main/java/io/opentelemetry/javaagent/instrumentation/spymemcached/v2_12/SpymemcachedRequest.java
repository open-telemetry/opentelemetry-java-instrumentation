/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;

@AutoValue
public abstract class SpymemcachedRequest {

  public static SpymemcachedRequest create(MemcachedConnection connection, String queryText) {
    return new AutoValue_SpymemcachedRequest(
        connection, queryText, SpymemcachedServerTargets.get(connection));
  }

  public abstract MemcachedConnection getConnection();

  public abstract String getQueryText();

  @Nullable
  public abstract DbServerTarget getServerTarget();

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

  /** Returns the memcached command that corresponds to the client method. */
  String getStableOperationName() {
    String operationName = getOperationName();
    switch (operationName) {
      case "getBulk":
        // getBulk is get with multiple keys.
        return "get";
      case "getAndTouch":
        return "gat";
      default:
        return operationName;
    }
  }

  public String getOperationName() {
    String queryText = getQueryText();
    if (queryText.startsWith("async")) {
      queryText = queryText.substring("async".length());
    }
    if (queryText.startsWith("CAS")) {
      // 'CAS' name is special, we have to lowercase whole name
      return "cas" + queryText.substring("CAS".length());
    }

    char[] chars = queryText.toCharArray();
    // Lowercase first letter
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }
}
