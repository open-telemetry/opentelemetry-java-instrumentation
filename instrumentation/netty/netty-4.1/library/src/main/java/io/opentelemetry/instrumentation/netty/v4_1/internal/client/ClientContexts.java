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
        // Finagle transfers an HTTP/1 upgrade's attributes to the HTTP/2 stream channel.
        Context context = channel.attr(AttributeKeys.CLIENT_CONTEXT).get();
        NettyCommonRequest request = channel.attr(HTTP_CLIENT_REQUEST).get();
        if (context != null && request != null) {
          Context parent = channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).getAndSet(null);
          RequestState transferred = takeUpgradedRequest(channel, request);
          if (transferred != null) {
            transferred.upgraded = false;
            contexts.requests.add(transferred);
          } else if (channel.parent() == null || channel.parent().attr(KEY).get() == null) {
            contexts.requests.add(
                new RequestState(parent == null ? Context.root() : parent, context, request));
          }
          // If the parent's FIFO already owned this request but no longer contains it, its
          // lifecycle has already ended. Do not reconstruct a second completion owner.
          contexts.publish(channel);
        }
        attribute.set(contexts);
      }
      return contexts;
    }
  }

  @Nullable
  private static RequestState takeUpgradedRequest(Channel channel, NettyCommonRequest request) {
    Channel parent = channel.parent();
    ClientContexts contexts = parent == null ? null : parent.attr(KEY).get();
    if (contexts != null && parent != null) {
      synchronized (contexts) {
        for (RequestState state : contexts.requests) {
          if (state.request == request) {
            contexts.requests.remove(state);
            contexts.publish(parent);
            parent.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).compareAndSet(state.parent, null);
            return state;
          }
        }
      }
    }
    return null;
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

  void fail(
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

  /** Ends outstanding spans, disabling correlation when an exception makes responses ambiguous. */
  public static void end(
      Channel channel,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      @Nullable Throwable cause,
      boolean clear) {
    ClientContexts contexts = channel.hasAttr(KEY) ? channel.attr(KEY).get() : null;
    if (contexts == null) {
      if (!channel.hasAttr(AttributeKeys.CLIENT_CONTEXT)
          || !channel.hasAttr(HTTP_CLIENT_REQUEST)
          || channel.attr(AttributeKeys.CLIENT_CONTEXT).get() == null
          || channel.attr(HTTP_CLIENT_REQUEST).get() == null) {
        return;
      }
      // Finagle may copy the HTTP/1 upgrade state before the stream has initialized its handlers.
      contexts = get(channel);
    }
    if (cause != null && !clear) {
      contexts.fail(channel, instrumenter, cause);
      return;
    }
    List<RequestState> states;
    synchronized (contexts) {
      states = new ArrayList<>(contexts.requests);
      if (clear) {
        contexts.requests.clear();
        contexts.publish(channel);
        channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(null);
        contexts.failureParent = null;
      }
    }
    for (RequestState state : states) {
      state.end(
          instrumenter,
          clear && state.upgraded && cause instanceof ClosedChannelException ? null : cause);
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
