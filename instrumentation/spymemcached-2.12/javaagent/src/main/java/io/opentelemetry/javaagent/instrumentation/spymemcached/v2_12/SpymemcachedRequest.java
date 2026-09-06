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
import java.util.Iterator;
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
  private long wholeRequestTicket = -1;
  @Nullable private Map<String, NodeCapture> handlingNodesByKey;
  private boolean hasMultipleHandlingNodes;
  private long generation;
  private long nextTicket;

  public void setHandlingNode(@Nullable MemcachedNode node) {
    if (node == null) {
      return;
    }
    CaptureToken token = beginCapture();
    InetSocketAddress address = captureNodeAddress(node);
    applyCapture(token, node, address, null);
  }

  public void setHandlingNode(@Nullable MemcachedNode node, Collection<String> keys) {
    if (node == null) {
      return;
    }
    CaptureToken token = beginCapture();
    InetSocketAddress address = captureNodeAddress(node);
    applyCapture(token, node, address, keys);
  }

  public void setRetryHandlingNode(@Nullable MemcachedNode node) {
    if (node == null) {
      return;
    }
    CaptureToken token;
    synchronized (lock) {
      generation++;
      token = new CaptureToken(generation, ++nextTicket);
      handlingNodesByKey = null;
      hasMultipleHandlingNodes = false;
      handlingNode = null;
      handlingNodeAddress = null;
      wholeRequestNode = null;
      wholeRequestTicket = -1;
    }
    InetSocketAddress address = captureNodeAddress(node);
    applyCapture(token, node, address, null);
  }

  public void clearHandlingNode() {
    synchronized (lock) {
      generation++;
      nextTicket++;
      hasMultipleHandlingNodes = true;
      handlingNodesByKey = null;
      handlingNode = null;
      handlingNodeAddress = null;
      wholeRequestNode = null;
      wholeRequestTicket = -1;
    }
  }

  @Nullable
  public InetSocketAddress getHandlingNodeAddress() {
    synchronized (lock) {
      return handlingNodeAddress;
    }
  }

  private CaptureToken beginCapture() {
    synchronized (lock) {
      return new CaptureToken(generation, ++nextTicket);
    }
  }

  @Nullable
  private static InetSocketAddress captureNodeAddress(MemcachedNode node) {
    SocketAddress socketAddress = node.getSocketAddress();
    return socketAddress instanceof InetSocketAddress ? (InetSocketAddress) socketAddress : null;
  }

  private void applyCapture(
      CaptureToken token,
      MemcachedNode node,
      @Nullable InetSocketAddress address,
      @Nullable Collection<String> keys) {
    synchronized (lock) {
      if (token.generation != generation || hasMultipleHandlingNodes) {
        return;
      }

      if (keys == null || keys.isEmpty()) {
        if (wholeRequestNode != null && wholeRequestNode != node) {
          markMultipleHandlingNodes();
          return;
        }
        if (wholeRequestNode != null && wholeRequestTicket > token.ticket) {
          return;
        }
        if (handlingNode != null && handlingNode != node) {
          markMultipleHandlingNodes();
          return;
        }
        wholeRequestNode = node;
        wholeRequestTicket = token.ticket;
        handlingNode = node;
        handlingNodeAddress = address;
        return;
      }

      NodeCapture capture = new NodeCapture(node, address, token.ticket);
      Map<String, NodeCapture> nodesByKey = handlingNodesByKey;
      if (nodesByKey == null) {
        nodesByKey = new HashMap<>();
        handlingNodesByKey = nodesByKey;
      }
      // Request associations provide an instrumentation-owned, stable key set.
      for (String key : keys) {
        NodeCapture existing = nodesByKey.get(key);
        if (existing == null || existing.ticket <= capture.ticket) {
          nodesByKey.put(key, capture);
        }
      }

      updateHandlingNode(nodesByKey);
    }
  }

  private void updateHandlingNode(Map<String, NodeCapture> nodesByKey) {
    Iterator<NodeCapture> iterator = nodesByKey.values().iterator();
    NodeCapture singleCapture = iterator.next();
    while (iterator.hasNext()) {
      NodeCapture capture = iterator.next();
      if (singleCapture.node != capture.node) {
        handlingNode = null;
        handlingNodeAddress = null;
        return;
      }
      if (capture.ticket > singleCapture.ticket) {
        singleCapture = capture;
      }
    }
    handlingNode = singleCapture.node;
    handlingNodeAddress = singleCapture.address;
  }

  private void markMultipleHandlingNodes() {
    hasMultipleHandlingNodes = true;
    handlingNodesByKey = null;
    handlingNode = null;
    handlingNodeAddress = null;
    wholeRequestNode = null;
    wholeRequestTicket = -1;
  }

  private static class CaptureToken {
    private final long generation;
    private final long ticket;

    private CaptureToken(long generation, long ticket) {
      this.generation = generation;
      this.ticket = ticket;
    }
  }

  private static class NodeCapture {
    private final MemcachedNode node;
    @Nullable private final InetSocketAddress address;
    private final long ticket;

    private NodeCapture(MemcachedNode node, @Nullable InetSocketAddress address, long ticket) {
      this.node = node;
      this.address = address;
      this.ticket = ticket;
    }
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
