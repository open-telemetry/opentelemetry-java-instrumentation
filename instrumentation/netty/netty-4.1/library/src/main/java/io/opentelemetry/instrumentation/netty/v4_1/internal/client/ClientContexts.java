/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.client;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientRequestTracingHandler.HTTP_CLIENT_REQUEST;
import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ClientContexts {
  private static final AttributeKey<ClientContexts> KEY =
      AttributeKey.valueOf(ClientContexts.class, "client-contexts");

  private final Channel channel;
  @Nullable private RequestState firstRequest;
  @Nullable private ArrayDeque<RequestState> overflowRequests;
  @Nullable private RequestState outboundRequest;
  @Nullable private RequestState publishedHead;
  private boolean published;
  @Nullable private Throwable failure;

  private ClientContexts(Channel channel) {
    this.channel = channel;
  }

  static ClientContexts get(Channel channel) {
    Attribute<ClientContexts> attribute = channel.attr(KEY);
    ClientContexts contexts = attribute.get();
    if (contexts == null) {
      ClientContexts newContexts = new ClientContexts(channel);
      contexts = attribute.setIfAbsent(newContexts);
      if (contexts == null) {
        contexts = newContexts;
      }
    }
    return contexts;
  }

  @Nullable
  static RequestState getOutboundRequest(Channel channel) {
    if (!channel.hasAttr(KEY)) {
      return null;
    }
    ClientContexts contexts = channel.attr(KEY).get();
    return contexts == null ? null : contexts.outboundRequest((Object) null);
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
      RequestState state = parentContexts.firstRequest;
      if (state == null || !state.upgraded) {
        return;
      }
      Attribute<ClientContexts> streamAttribute = stream.attr(KEY);
      ClientContexts streamContexts = streamAttribute.get();
      if (streamContexts == null) {
        ClientContexts newContexts = new ClientContexts(stream);
        streamContexts = streamAttribute.setIfAbsent(newContexts);
        if (streamContexts == null) {
          streamContexts = newContexts;
        }
      }
      synchronized (streamContexts) {
        if (!streamContexts.isEmpty()) {
          return;
        }
        parentContexts.removeFirst();
        if (parentContexts.outboundRequest == state) {
          parentContexts.outboundRequest = null;
        }
        parentContexts.publish(parent);
        state.upgraded = false;
        state.response = null;
        state.owner = streamContexts;
        streamContexts.addRequest(state);
        streamContexts.publish(stream);
      }
    }
  }

  Context parent(Channel channel) {
    Context parent = channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).getAndSet(null);
    return parent == null ? Context.current() : parent;
  }

  synchronized boolean isUsable() {
    return failure == null;
  }

  synchronized boolean add(Channel channel, RequestState state, boolean hasMoreContent) {
    if (failure != null) {
      return false;
    }
    boolean first = firstRequest == null;
    addRequest(state);
    if (hasMoreContent) {
      outboundRequest = state;
    }
    if (first) {
      publish(channel);
    }
    return true;
  }

  synchronized RequestState outboundRequest(Object msg) {
    RequestState state = outboundRequest;
    if (msg instanceof LastHttpContent) {
      outboundRequest = null;
    }
    return state;
  }

  synchronized Throwable failure() {
    return requireNonNull(failure);
  }

  @Nullable
  synchronized Context callbackParent() {
    RequestState state = firstRequest;
    return state == null ? null : state.parent;
  }

  @Nullable
  Context failRequests(
      Channel channel,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      Throwable cause) {
    return failRequestsWithOwner(channel, instrumenter, cause, null);
  }

  private static void failRequest(
      RequestState state,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      Throwable cause) {
    ClientContexts owner = state.owner;
    owner.failRequestsWithOwner(owner.channel, instrumenter, cause, state);
  }

  @Nullable
  private Context failRequestsWithOwner(
      Channel channel,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      Throwable cause,
      @Nullable RequestState failedState) {
    @Nullable List<RequestState> states = null;
    ClientContexts transferredOwner = null;
    Context parent = null;
    synchronized (this) {
      if (failure == null) {
        if (failedState != null && !contains(failedState)) {
          if (failedState.owner != this) {
            transferredOwner = failedState.owner;
          } else {
            return null;
          }
        } else if (firstRequest == null) {
          return null;
        } else {
          failure = cause;
          parent = firstRequest.parent;
        }
      }
      if (transferredOwner == null) {
        states = drainRequests();
        publish(channel);
        channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(null);
      }
    }
    if (transferredOwner != null) {
      return transferredOwner.failRequestsWithOwner(
          transferredOwner.channel, instrumenter, cause, failedState);
    }
    // A failed write or decoder exception can make the remaining responses ambiguous. Neither
    // retaining nor removing a FIFO entry permits safe response attribution after this point.
    for (RequestState state : requireNonNull(states)) {
      state.end(instrumenter, cause);
    }
    return parent;
  }

  private boolean contains(RequestState state) {
    return firstRequest == state || (overflowRequests != null && overflowRequests.contains(state));
  }

  @Nullable
  synchronized RequestState peek() {
    return firstRequest;
  }

  synchronized void remove(RequestState state) {
    if (firstRequest == state) {
      removeFirst();
    } else if (overflowRequests != null) {
      overflowRequests.remove(state);
      if (overflowRequests.isEmpty()) {
        overflowRequests = null;
      }
    }
  }

  synchronized void publish(Channel channel) {
    RequestState state = firstRequest;
    if (published && publishedHead == state) {
      return;
    }
    channel.attr(AttributeKeys.CLIENT_CONTEXT).set(state == null ? null : state.context);
    channel.attr(HTTP_CLIENT_REQUEST).set(state == null ? null : state.request);
    publishedHead = state;
    published = true;
  }

  private boolean isEmpty() {
    return firstRequest == null;
  }

  private void addRequest(RequestState state) {
    if (firstRequest == null) {
      firstRequest = state;
    } else {
      if (overflowRequests == null) {
        overflowRequests = new ArrayDeque<>();
      }
      overflowRequests.addLast(state);
    }
  }

  @Nullable
  private RequestState removeFirst() {
    RequestState state = firstRequest;
    if (state == null) {
      return null;
    }
    if (overflowRequests == null || overflowRequests.isEmpty()) {
      firstRequest = null;
      overflowRequests = null;
    } else {
      firstRequest = overflowRequests.removeFirst();
      if (overflowRequests.isEmpty()) {
        overflowRequests = null;
      }
    }
    return state;
  }

  private List<RequestState> drainRequests() {
    List<RequestState> states = new ArrayList<>();
    if (firstRequest != null) {
      states.add(firstRequest);
    }
    if (overflowRequests != null) {
      states.addAll(overflowRequests);
    }
    firstRequest = null;
    overflowRequests = null;
    outboundRequest = null;
    return states;
  }

  /** Ends outstanding spans and disables correlation when a failure makes responses ambiguous. */
  @Nullable
  public static Context fail(
      Channel channel,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      Throwable cause) {
    ClientContexts contexts = channel.hasAttr(KEY) ? channel.attr(KEY).get() : null;
    return contexts == null ? null : contexts.failRequests(channel, instrumenter, cause);
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
      states = contexts.drainRequests();
      contexts.publish(channel);
      channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(null);
    }
    Throwable closeCause = cause;
    if (closeCause == null && !states.isEmpty()) {
      closeCause = new ClosedChannelException();
    }
    for (RequestState state : states) {
      state.end(
          instrumenter,
          state.upgraded && closeCause instanceof ClosedChannelException ? null : closeCause);
    }
  }

  static final class RequestState implements ChannelFutureListener {
    private final AtomicBoolean ended = new AtomicBoolean();
    private volatile ClientContexts owner;
    private final Instrumenter<NettyCommonRequest, HttpResponse> instrumenter;
    private final ChannelHandlerContext handlerContext;
    private final ChannelFutureListener voidFailureListener = new VoidFailureListener();
    final Context parent;
    @Nullable final Context context;
    final NettyCommonRequest request;
    @Nullable volatile HttpResponse response;
    boolean waitingForFinalResponse;
    boolean upgraded;

    RequestState(
        ClientContexts owner,
        Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
        ChannelHandlerContext handlerContext,
        Context parent,
        @Nullable Context context,
        NettyCommonRequest request) {
      this.owner = owner;
      this.instrumenter = instrumenter;
      this.handlerContext = handlerContext;
      this.parent = parent;
      this.context = context;
      this.request = request;
    }

    ChannelFutureListener voidFailureListener() {
      return voidFailureListener;
    }

    void fail(Throwable cause) {
      failRequest(this, instrumenter, cause);
    }

    @Override
    public void operationComplete(ChannelFuture future) {
      if (!future.isSuccess()) {
        failRequest(this, instrumenter, future.cause());
      }
    }

    void end(
        Instrumenter<NettyCommonRequest, HttpResponse> instrumenter, @Nullable Throwable cause) {
      if (ended.compareAndSet(false, true)) {
        if (context != null) {
          instrumenter.end(context, request, response, cause);
        }
      }
    }

    private final class VoidFailureListener implements ChannelFutureListener {
      @Override
      public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
          Throwable cause = future.cause();
          failRequest(RequestState.this, instrumenter, cause);
          if (handlerContext.channel().isRegistered()) {
            // Start after this handler so a late failure cannot fail the next pending request.
            handlerContext.fireExceptionCaught(cause);
          }
        }
      }
    }
  }
}
