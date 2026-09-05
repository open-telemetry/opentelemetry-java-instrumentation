/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
  @Nullable private InetSocketAddress handlingNodeAddress;
  @Nullable private NodeCapture wholeRequestCapture;
  @Nullable private Map<String, NodeCapture> handlingNodesByKey;
  private boolean hasMultipleHandlingNodes;
  private long generation;
  private long nextTicket;

  public void setHandlingNode(@Nullable MemcachedNode node) {
    if (node == null) {
      return;
    }
    CaptureToken token = beginCapture();
    NodeCapture capture = captureNode(node, token.ticket);
    applyCapture(token, capture, new String[0]);
  }

  public void setHandlingNode(@Nullable MemcachedNode node, Collection<String> keys) {
    if (node == null) {
      return;
    }
    List<String> keySnapshot = new ArrayList<>(keys);
    if (keySnapshot.isEmpty()) {
      setHandlingNode(node);
      return;
    }
    CaptureToken token = beginCapture();
    NodeCapture capture = captureNode(node, token.ticket);
    applyCapture(token, capture, keySnapshot.toArray(new String[0]));
  }

  public void setRetryHandlingNode(@Nullable MemcachedNode node) {
    if (node == null) {
      return;
    }
    CaptureToken token;
    synchronized (lock) {
      generation++;
      token = new CaptureToken(generation, ++nextTicket);
      wholeRequestCapture = null;
      handlingNodesByKey = null;
      hasMultipleHandlingNodes = false;
      handlingNodeAddress = null;
    }
    NodeCapture capture = captureNode(node, token.ticket);
    applyCapture(token, capture, new String[0]);
  }

  public void clearHandlingNode() {
    synchronized (lock) {
      generation++;
      nextTicket++;
      hasMultipleHandlingNodes = true;
      wholeRequestCapture = null;
      handlingNodesByKey = null;
      handlingNodeAddress = null;
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

  private static NodeCapture captureNode(MemcachedNode node, long ticket) {
    SocketAddress socketAddress = node.getSocketAddress();
    InetSocketAddress address = null;
    if (socketAddress instanceof InetSocketAddress) {
      address = (InetSocketAddress) socketAddress;
    }
    return new NodeCapture(node, address, ticket);
  }

  private void applyCapture(CaptureToken token, NodeCapture capture, String[] keys) {
    synchronized (lock) {
      if (token.generation != generation || hasMultipleHandlingNodes) {
        return;
      }

      if (keys.length == 0) {
        if (wholeRequestCapture != null && wholeRequestCapture.ticket > capture.ticket) {
          return;
        }
        if (wholeRequestCapture != null && wholeRequestCapture.node != capture.node) {
          hasMultipleHandlingNodes = true;
          wholeRequestCapture = null;
          handlingNodesByKey = null;
          handlingNodeAddress = null;
          return;
        }
        wholeRequestCapture = capture;
      } else {
        if (handlingNodesByKey == null) {
          handlingNodesByKey = new HashMap<>();
        }
        for (String key : keys) {
          NodeCapture existing = handlingNodesByKey.get(key);
          if (existing == null || existing.ticket <= capture.ticket) {
            handlingNodesByKey.put(key, capture);
          }
        }
      }

      updateHandlingNode();
    }
  }

  private void updateHandlingNode() {
    if (handlingNodesByKey != null && !handlingNodesByKey.isEmpty()) {
      NodeCapture singleCapture = null;
      for (NodeCapture capture : handlingNodesByKey.values()) {
        if (singleCapture != null && singleCapture.node != capture.node) {
          handlingNodeAddress = null;
          return;
        }
        singleCapture = capture;
      }
      handlingNodeAddress = singleCapture.address;
      return;
    }

    if (wholeRequestCapture == null) {
      handlingNodeAddress = null;
    } else {
      handlingNodeAddress = wholeRequestCapture.address;
    }
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
