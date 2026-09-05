/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.couchbase.client.core.cnc.RequestSpan;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;

public class CouchbaseRequestPeers {

  private static final ThreadLocal<Deque<Scope>> current = new ThreadLocal<>();

  @Nullable
  public static Scope open(@Nullable RequestSpan parent, @Nullable SocketAddress remoteAddress) {
    if (!emitStableDatabaseSemconv()
        || parent == null
        || !(remoteAddress instanceof InetSocketAddress)) {
      return null;
    }
    InetSocketAddress socketAddress = (InetSocketAddress) remoteAddress;
    if (socketAddress.isUnresolved()) {
      return null;
    }
    Scope scope = new Scope(parent, socketAddress);
    Deque<Scope> scopes = current.get();
    if (scopes == null) {
      scopes = new ArrayDeque<>();
      current.set(scopes);
    }
    scopes.push(scope);
    return scope;
  }

  @Nullable
  public static Peer consume(@Nullable RequestSpan parent) {
    if (parent == null) {
      return null;
    }
    Deque<Scope> scopes = current.get();
    if (scopes == null) {
      return null;
    }
    Scope scope = scopes.peek();
    if (scope == null || scope.parent != parent || scope.consumed) {
      return null;
    }
    scope.consumed = true;
    InetSocketAddress remoteAddress = scope.remoteAddress;
    return new Peer(remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort());
  }

  public static class Peer {

    private final String address;
    private final int port;

    private Peer(String address, int port) {
      this.address = address;
      this.port = port;
    }

    public String getAddress() {
      return address;
    }

    public int getPort() {
      return port;
    }
  }

  public static class Scope {

    private final RequestSpan parent;
    private final InetSocketAddress remoteAddress;
    private boolean consumed;

    private Scope(RequestSpan parent, InetSocketAddress remoteAddress) {
      this.parent = parent;
      this.remoteAddress = remoteAddress;
    }

    public void close() {
      Deque<Scope> scopes = current.get();
      if (scopes == null) {
        return;
      }
      if (scopes.peek() == this) {
        scopes.pop();
      } else {
        scopes.remove(this);
      }
      if (scopes.isEmpty()) {
        current.remove();
      }
    }
  }

  private CouchbaseRequestPeers() {}
}
