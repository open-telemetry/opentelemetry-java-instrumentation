/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A helper class for keeping track of incoming requests and spans associated with them.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ServerContexts {
  private static final int PIPELINING_LIMIT = 1000;
  // With http pipelining multiple requests can be sent on the same connection. Responses should be
  // sent in the same order the requests came in. We use this deque to store the request context
  // and pop elements as responses are sent.
  private final Deque<ServerContext> serverContexts = new ArrayDeque<>();
  private volatile boolean broken = false;

  private ServerContexts() {}

  public static ServerContexts get(Channel channel) {
    return channel.attr(AttributeKeys.SERVER_CONTEXTS).get();
  }

  public static ServerContexts getOrCreate(Channel channel) {
    Attribute<ServerContexts> attribute = channel.attr(AttributeKeys.SERVER_CONTEXTS);
    ServerContexts result = attribute.get();
    if (result == null) {
      result = new ServerContexts();
      attribute.set(result);
    }
    return result;
  }

  public static ServerContext peekFirst(Channel channel) {
    ServerContexts serverContexts = get(channel);
    return serverContexts != null ? serverContexts.peekFirst() : null;
  }

  public ServerContext peekFirst() {
    return serverContexts.peekFirst();
  }

  public ServerContext peekLast() {
    return serverContexts.peekFirst();
  }

  public ServerContext pollFirst() {
    return serverContexts.pollFirst();
  }

  public ServerContext pollLast() {
    return serverContexts.pollLast();
  }

  public void addLast(ServerContext context) {
    if (broken) {
      return;
    }
    // If the pipelining limit is exceeded we'll stop tracing and mark the channel as broken.
    // Exceeding the limit indicates that there is good chance that server context are not removed
    // from the deque and there could be a memory leak. This could happen when http server decides
    // not to send response to some requests, for example see
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/11942
    if (serverContexts.size() > PIPELINING_LIMIT) {
      broken = true;
      serverContexts.clear();
    }
    serverContexts.addLast(context);
  }
}
