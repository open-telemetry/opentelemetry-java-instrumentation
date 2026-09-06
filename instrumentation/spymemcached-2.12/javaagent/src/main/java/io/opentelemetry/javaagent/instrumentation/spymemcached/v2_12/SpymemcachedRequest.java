/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;

@AutoValue
public abstract class SpymemcachedRequest {

  public static SpymemcachedRequest create(MemcachedConnection connection, String queryText) {
    return new AutoValue_SpymemcachedRequest(
        connection, queryText, SpymemcachedSingletons.serverTarget(connection));
  }

  public abstract MemcachedConnection getConnection();

  public abstract String getQueryText();

  @Nullable
  public abstract DbServerTarget getServerTarget();

  private final Object lock = new Object();
  @Nullable private MemcachedNode handlingNode;
  @Nullable private InetSocketAddress handlingNodeAddress;
  @Nullable private MemcachedNode wholeRequestNode;
  @Nullable private Map<String, MemcachedNode> handlingNodesByKey;
  private boolean hasMultipleHandlingNodes;

  public void setHandlingNode(@Nullable MemcachedNode node) {
    if (node == null) {
      return;
    }
    synchronized (lock) {
      applyHandlingNode(node);
    }
  }

  public void setHandlingNode(@Nullable MemcachedNode node, Collection<String> keys) {
    if (node == null) {
      return;
    }
    synchronized (lock) {
      if (keys.isEmpty()) {
        applyHandlingNode(node);
        return;
      }
      if (hasMultipleHandlingNodes) {
        return;
      }
      // Request associations provide a stable, instrumentation-owned key set at this boundary.
      if (handlingNodesByKey == null) {
        handlingNodesByKey = new HashMap<>();
      }
      for (String key : keys) {
        handlingNodesByKey.put(key, node);
      }
      updateHandlingNode(handlingNodesByKey);
    }
  }

  public void setRetryHandlingNode(@Nullable MemcachedNode node) {
    if (node == null) {
      return;
    }
    synchronized (lock) {
      handlingNodesByKey = null;
      hasMultipleHandlingNodes = false;
      wholeRequestNode = node;
      handlingNode = node;
    }
  }

  public void clearHandlingNode() {
    synchronized (lock) {
      hasMultipleHandlingNodes = true;
      handlingNodesByKey = null;
      handlingNode = null;
      wholeRequestNode = null;
    }
  }

  @Nullable
  public InetSocketAddress getHandlingNodeAddress() {
    synchronized (lock) {
      return handlingNodeAddress;
    }
  }

  void captureHandlingNodeAddress() {
    MemcachedNode node;
    synchronized (lock) {
      node = handlingNode;
      handlingNodeAddress = null;
    }
    if (node == null) {
      return;
    }
    InetSocketAddress address = captureNodeAddress(node);
    synchronized (lock) {
      handlingNodeAddress = address;
    }
  }

  // The endpoint is sampled at completion, so custom nodes expose their then-current address.
  @Nullable
  private static InetSocketAddress captureNodeAddress(MemcachedNode node) {
    SocketAddress socketAddress = node.getSocketAddress();
    return socketAddress instanceof InetSocketAddress ? (InetSocketAddress) socketAddress : null;
  }

  private void applyHandlingNode(MemcachedNode node) {
    if (hasMultipleHandlingNodes) {
      return;
    }
    if (handlingNode == null && handlingNodesByKey != null) {
      markMultipleHandlingNodes();
      return;
    }
    if (wholeRequestNode != null && wholeRequestNode != node) {
      markMultipleHandlingNodes();
      return;
    }
    if (handlingNode != null && handlingNode != node) {
      markMultipleHandlingNodes();
      return;
    }
    wholeRequestNode = node;
    handlingNode = node;
  }

  private void updateHandlingNode(Map<String, MemcachedNode> nodesByKey) {
    MemcachedNode singleNode = null;
    for (MemcachedNode node : nodesByKey.values()) {
      if (singleNode != null && singleNode != node) {
        handlingNode = null;
        return;
      }
      singleNode = node;
    }
    handlingNode = singleNode;
  }

  private void markMultipleHandlingNodes() {
    hasMultipleHandlingNodes = true;
    handlingNodesByKey = null;
    handlingNode = null;
    wholeRequestNode = null;
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
