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
        connection, queryText, SpymemcachedSingletons.serverTarget(connection));
  }

  public abstract MemcachedConnection getConnection();

  public abstract String getQueryText();

  @Nullable
  public abstract DbServerTarget getServerTarget();

  // Sequential single-key retries replace the peer, as recommended by database semantic
  // conventions. Bulk and optimized retries suppress the address instead of tracking per-key
  // routing. Capture runs before queue publication, so initial updates cannot overwrite retries.
  // Once suppressHandlingNodeAddress is true, even an in-progress capture cannot expose a peer.
  @Nullable private MemcachedNode handlingNode;
  @Nullable private volatile InetSocketAddress handlingNodeAddress;
  private volatile boolean suppressHandlingNodeAddress;

  public void setHandlingNode(@Nullable MemcachedNode node) {
    setHandlingNode(node, false);
  }

  private void setHandlingNode(@Nullable MemcachedNode node, boolean retry) {
    if (node == null || suppressHandlingNodeAddress) {
      return;
    }
    if (!retry && handlingNode != null && node != handlingNode) {
      suppressHandlingNodeAddress = true;
      handlingNode = null;
      handlingNodeAddress = null;
      return;
    }

    handlingNode = node;
    SocketAddress socketAddress = node.getSocketAddress();
    handlingNodeAddress =
        socketAddress instanceof InetSocketAddress ? (InetSocketAddress) socketAddress : null;
  }

  public void setRetryHandlingNode(@Nullable MemcachedNode node) {
    setHandlingNode(node, true);
  }

  public void suppressHandlingNodeAddress() {
    suppressHandlingNodeAddress = true;
  }

  @Nullable
  public InetSocketAddress getHandlingNodeAddress() {
    return suppressHandlingNodeAddress ? null : handlingNodeAddress;
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
