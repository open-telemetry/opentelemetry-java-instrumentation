/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.client;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientRequestTracingHandler.HTTP_CLIENT_REQUEST;
import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ClientContexts {
  private static final AttributeKey<ClientContexts> KEY =
      AttributeKey.valueOf(ClientContexts.class, "client-contexts");

  private final Queue<RequestState> requests = new ArrayDeque<>();
  @Nullable private Throwable failure;
  @Nullable private Context failureParent;

  private ClientContexts() {}

  static ClientContexts get(Channel channel) {
    Attribute<ClientContexts> attribute = channel.attr(KEY);
    synchronized (attribute) {
      ClientContexts contexts = attribute.get();
      if (contexts == null) {
        contexts = new ClientContexts();
        attribute.set(contexts);
      }
      return contexts;
    }
  }

  public static void transferUpgradedRequest(Channel parent, Channel stream) {
    if (!parent.hasAttr(KEY)) {
      return;
    }
    ClientContexts parentContexts = parent.attr(KEY).get();
    if (parentContexts == null) {
      return;
    }
    synchronized (parentContexts) {
      RequestState state = parentContexts.requests.peek();
      if (state == null || !state.upgraded) {
        return;
      }
      Attribute<ClientContexts> streamAttribute = stream.attr(KEY);
      synchronized (streamAttribute) {
        ClientContexts streamContexts = streamAttribute.get();
        if (streamContexts == null) {
          streamContexts = new ClientContexts();
        }
        synchronized (streamContexts) {
          if (!streamContexts.requests.isEmpty()) {
            return;
          }
          parentContexts.requests.remove();
          parentContexts.publish(parent);
          state.upgraded = false;
          streamContexts.requests.add(state);
          streamContexts.publish(stream);
          streamAttribute.set(streamContexts);
        }
      }
    }
  }

  Context parent(Channel channel) {
    synchronized (this) {
      failureParent = null;
    }
    Context parent = channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).getAndSet(null);
    return parent == null ? Context.current() : parent;
  }

  synchronized boolean isUsable() {
    return failure == null;
  }

  synchronized boolean add(Channel channel, RequestState state) {
    if (failure != null) {
      return false;
    }
    requests.add(state);
    if (requests.peek() == state) {
      publish(channel);
    }
    return true;
  }

  synchronized Throwable failure() {
    return requireNonNull(failure);
  }

  @Nullable
  synchronized Context callbackParent() {
    RequestState state = requests.peek();
    return state == null ? null : state.parent;
  }

  @Nullable
  synchronized Context exceptionParent(Throwable cause) {
    Context parent = failure == cause ? failureParent : null;
    failureParent = null;
    return parent;
  }

  void failRequests(
      Channel channel,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      Throwable cause) {
    List<RequestState> states;
    synchronized (this) {
      if (failure == null) {
        failure = cause;
        RequestState first = requests.peek();
        failureParent = first == null ? null : first.parent;
      }
      states = new ArrayList<>(requests);
      requests.clear();
      publish(channel);
      channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(null);
    }
    // A failed write or decoder exception can make the remaining responses ambiguous. Neither
    // retaining nor removing a FIFO entry permits safe response attribution after this point.
    for (RequestState state : states) {
      state.end(instrumenter, cause);
    }
  }

  @Nullable
  synchronized RequestState peek() {
    return requests.peek();
  }

  synchronized void remove(RequestState state) {
    requests.remove(state);
  }

  synchronized void publish(Channel channel) {
    RequestState state = requests.peek();
    channel.attr(AttributeKeys.CLIENT_CONTEXT).set(state == null ? null : state.context);
    channel.attr(HTTP_CLIENT_REQUEST).set(state == null ? null : state.request);
  }

  /** Ends outstanding spans and disables correlation when a failure makes responses ambiguous. */
  public static void fail(
      Channel channel,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      Throwable cause) {
    ClientContexts contexts = channel.hasAttr(KEY) ? channel.attr(KEY).get() : null;
    if (contexts != null) {
      contexts.failRequests(channel, instrumenter, cause);
    }
  }

  /** Ends outstanding spans when the channel closes or becomes inactive. */
  public static void close(
      Channel channel,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      @Nullable Throwable cause) {
    ClientContexts contexts = channel.hasAttr(KEY) ? channel.attr(KEY).get() : null;
    if (contexts == null) {
      return;
    }
    List<RequestState> states;
    synchronized (contexts) {
      states = new ArrayList<>(contexts.requests);
      contexts.requests.clear();
      contexts.publish(channel);
      channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(null);
      contexts.failureParent = null;
    }
    for (RequestState state : states) {
      state.end(
          instrumenter, state.upgraded && cause instanceof ClosedChannelException ? null : cause);
    }
  }

  static final class RequestState {
    private final AtomicBoolean ended = new AtomicBoolean();
    final Context parent;
    @Nullable final Context context;
    final NettyCommonRequest request;
    @Nullable volatile HttpResponse response;
    boolean waitingForFinalResponse;
    boolean upgraded;

    RequestState(Context parent, @Nullable Context context, NettyCommonRequest request) {
      this.parent = parent;
      this.context = context;
      this.request = request;
    }

    void end(
        Instrumenter<NettyCommonRequest, HttpResponse> instrumenter, @Nullable Throwable cause) {
      if (ended.compareAndSet(false, true)) {
        if (context != null) {
          instrumenter.end(context, request, response, cause);
        }
      }
    }
  }
}
