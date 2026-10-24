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
import java.util.Arrays;
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
  private static final ThreadLocal<ResponseCallbackScope> RESPONSE_CALLBACK = new ThreadLocal<>();
  private static final ThreadLocal<ReadDispatch> READ_DISPATCH = new ThreadLocal<>();
  private static final ThreadLocal<FailureNotificationScope> FAILURE_NOTIFICATION =
      new ThreadLocal<>();

  private final Channel channel;
  @Nullable private RequestState firstRequest;
  @Nullable private ArrayDeque<RequestState> overflowRequests;
  @Nullable private RequestState outboundRequest;
  @Nullable private RequestState publishedHead;
  private boolean published;
  @Nullable private Throwable failure;
  @Nullable private Context failureParent;

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
    synchronized (this) {
      failureParent = null;
    }
    Context parent = channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).getAndSet(null);
    return parent == null ? Context.current() : parent;
  }

  static ResponseCallbackScope beginResponseCallback(RequestState state, Object msg) {
    ResponseCallbackScope previous = RESPONSE_CALLBACK.get();
    RESPONSE_CALLBACK.set(new ResponseCallbackScope(new ResponseCallback(state), msg));
    return previous;
  }

  static void endResponseCallback(@Nullable ResponseCallbackScope previous) {
    if (previous == null) {
      RESPONSE_CALLBACK.remove();
    } else {
      RESPONSE_CALLBACK.set(previous);
    }
  }

  @Nullable
  public static ResponseCallback currentResponseCallback(Channel channel, Object msg) {
    ResponseCallbackScope scope = RESPONSE_CALLBACK.get();
    if (scope == null || scope.callback.state.owner.channel != channel || scope.message != msg) {
      return null;
    }
    return scope.callback;
  }

  @Nullable
  public static ResponseCallback currentResponseCallback(Object msg) {
    ResponseCallbackScope scope = RESPONSE_CALLBACK.get();
    return scope == null || scope.message != msg ? null : scope.callback;
  }

  public static boolean hasResponseCallback(Channel channel) {
    ResponseCallbackScope scope = RESPONSE_CALLBACK.get();
    return scope != null && scope.callback.state.owner.channel == channel;
  }

  public static ResponseCallbackDispatch responseCallbackForMessage(
      ResponseCallback callback, Object msg) {
    return new ResponseCallbackDispatch(callback, msg);
  }

  @Nullable
  public static FailureNotification currentFailureNotification(Channel channel, Throwable cause) {
    for (FailureNotificationScope scope = FAILURE_NOTIFICATION.get();
        scope != null;
        scope = scope.previous) {
      FailureNotification notification = scope.notification;
      if (notification.channel == channel && notification.cause == cause) {
        return notification;
      }
    }
    return null;
  }

  @Nullable
  public static DispatchScope enterDispatch(
      @Nullable ResponseCallbackDispatch responseCallback,
      @Nullable FailureNotification failureNotification) {
    if (responseCallback == null && failureNotification == null) {
      return null;
    }
    ResponseCallbackScope previousCallback = RESPONSE_CALLBACK.get();
    FailureNotificationScope previousFailure = FAILURE_NOTIFICATION.get();
    if (responseCallback != null) {
      RESPONSE_CALLBACK.set(
          new ResponseCallbackScope(responseCallback.callback, responseCallback.message));
    }
    if (failureNotification != null) {
      FAILURE_NOTIFICATION.set(new FailureNotificationScope(failureNotification, previousFailure));
    }
    return new DispatchScope(previousCallback, previousFailure);
  }

  public static void exitDispatch(@Nullable DispatchScope scope) {
    if (scope == null) {
      return;
    }
    if (scope.previousCallback == null) {
      RESPONSE_CALLBACK.remove();
    } else {
      RESPONSE_CALLBACK.set(scope.previousCallback);
    }
    if (scope.previousFailure == null) {
      FAILURE_NOTIFICATION.remove();
    } else {
      FAILURE_NOTIFICATION.set(scope.previousFailure);
    }
  }

  public static int enterChannelRead(Channel channel, Object msg) {
    return enterChannelRead(channel, msg, null);
  }

  public static int enterChannelRead(
      Channel channel, Object msg, @Nullable ResponseCallback callback) {
    if (callback != null && callback.state.owner.channel != channel) {
      callback = null;
    }
    ResponseCallbackScope current = RESPONSE_CALLBACK.get();
    if (callback == null
        && current != null
        && current.callback.state.owner.channel == channel
        && current.message == msg) {
      callback = current.callback;
    }
    if (callback == null && (current == null || current.callback.state.owner.channel != channel)) {
      return -1;
    }
    ReadDispatch dispatch = READ_DISPATCH.get();
    if (dispatch == null) {
      dispatch = new ReadDispatch();
      READ_DISPATCH.set(dispatch);
    }
    int previousDepth = dispatch.depth;
    dispatch.push(channel, msg, current);
    if (callback == null) {
      RESPONSE_CALLBACK.remove();
    } else {
      RESPONSE_CALLBACK.set(new ResponseCallbackScope(callback, msg));
    }
    return previousDepth;
  }

  public static void exitChannelRead(int previousDepth) {
    if (previousDepth < 0) {
      return;
    }
    ReadDispatch dispatch = READ_DISPATCH.get();
    if (dispatch != null) {
      dispatch.clearTo(previousDepth);
    }
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
  synchronized Context exceptionParent(Throwable cause) {
    Context parent = failure == cause ? failureParent : null;
    failureParent = null;
    return parent;
  }

  void failRequests(
      Channel channel,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      Throwable cause) {
    failRequestsWithOwner(channel, instrumenter, cause, null);
  }

  private static void failRequest(
      RequestState state,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      Throwable cause) {
    ClientContexts owner = state.owner;
    owner.failRequestsWithOwner(owner.channel, instrumenter, cause, state);
  }

  private void failRequestsWithOwner(
      Channel channel,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      Throwable cause,
      @Nullable RequestState failedState) {
    @Nullable List<RequestState> states = null;
    ClientContexts transferredOwner = null;
    synchronized (this) {
      if (failure == null) {
        if (failedState != null && !contains(failedState)) {
          if (failedState.owner != this) {
            transferredOwner = failedState.owner;
          } else {
            return;
          }
        } else if (firstRequest == null) {
          return;
        } else {
          failure = cause;
          failureParent = firstRequest.parent;
        }
      }
      if (transferredOwner == null) {
        states = drainRequests();
        publish(channel);
        channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(null);
      }
    }
    if (transferredOwner != null) {
      transferredOwner.failRequestsWithOwner(
          transferredOwner.channel, instrumenter, cause, failedState);
      return;
    }
    // A failed write or decoder exception can make the remaining responses ambiguous. Neither
    // retaining nor removing a FIFO entry permits safe response attribution after this point.
    for (RequestState state : requireNonNull(states)) {
      state.end(instrumenter, cause);
    }
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
  public static void fail(
      Channel channel,
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      Throwable cause) {
    if (isWriteFailureNotification(channel, cause)) {
      return;
    }
    ClientContexts contexts = channel.hasAttr(KEY) ? channel.attr(KEY).get() : null;
    if (contexts != null && !isResponseCallbackException(channel)) {
      contexts.failRequests(channel, instrumenter, cause);
    }
  }

  private static boolean isResponseCallbackException(Channel channel) {
    ResponseCallbackScope callback = RESPONSE_CALLBACK.get();
    if (callback == null || callback.callback.state.owner.channel != channel) {
      return false;
    }
    ReadDispatch dispatch = READ_DISPATCH.get();
    return dispatch != null
        && dispatch.depth > 0
        && dispatch.channels[dispatch.depth - 1] == channel
        && dispatch.messages[dispatch.depth - 1] == callback.message;
  }

  private static boolean isWriteFailureNotification(Channel channel, Throwable cause) {
    for (FailureNotificationScope scope = FAILURE_NOTIFICATION.get();
        scope != null;
        scope = scope.previous) {
      FailureNotification notification = scope.notification;
      if (notification.channel == channel && notification.cause == cause) {
        return true;
      }
    }
    return false;
  }

  private static void fireWriteFailure(Channel channel, Throwable cause) {
    if (!channel.isRegistered()) {
      return;
    }
    FailureNotification notification = new FailureNotification(channel, cause);
    FailureNotificationScope previous = FAILURE_NOTIFICATION.get();
    FAILURE_NOTIFICATION.set(new FailureNotificationScope(notification, previous));
    try {
      channel.pipeline().fireExceptionCaught(cause);
    } finally {
      if (previous == null) {
        FAILURE_NOTIFICATION.remove();
      } else {
        FAILURE_NOTIFICATION.set(previous);
      }
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
      states = contexts.drainRequests();
      contexts.publish(channel);
      channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(null);
      contexts.failureParent = null;
    }
    for (RequestState state : states) {
      state.end(
          instrumenter, state.upgraded && cause instanceof ClosedChannelException ? null : cause);
    }
  }

  static final class RequestState implements ChannelFutureListener {
    private final AtomicBoolean ended = new AtomicBoolean();
    private volatile ClientContexts owner;
    private final Instrumenter<NettyCommonRequest, HttpResponse> instrumenter;
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
        Context parent,
        @Nullable Context context,
        NettyCommonRequest request) {
      this.owner = owner;
      this.instrumenter = instrumenter;
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
          fireWriteFailure(future.channel(), cause);
        }
      }
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static final class ResponseCallback {
    private final RequestState state;

    private ResponseCallback(RequestState state) {
      this.state = state;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static final class ResponseCallbackDispatch {
    private final ResponseCallback callback;
    private final Object message;

    private ResponseCallbackDispatch(ResponseCallback callback, Object message) {
      this.callback = callback;
      this.message = message;
    }
  }

  static final class ResponseCallbackScope {
    private final ResponseCallback callback;
    private final Object message;

    private ResponseCallbackScope(ResponseCallback callback, Object message) {
      this.callback = callback;
      this.message = message;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static final class FailureNotification {
    private final Channel channel;
    private final Throwable cause;

    private FailureNotification(Channel channel, Throwable cause) {
      this.channel = channel;
      this.cause = cause;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static final class DispatchScope {
    @Nullable private final ResponseCallbackScope previousCallback;
    @Nullable private final FailureNotificationScope previousFailure;

    private DispatchScope(
        @Nullable ResponseCallbackScope previousCallback,
        @Nullable FailureNotificationScope previousFailure) {
      this.previousCallback = previousCallback;
      this.previousFailure = previousFailure;
    }
  }

  private static final class FailureNotificationScope {
    private final FailureNotification notification;
    @Nullable private final FailureNotificationScope previous;

    private FailureNotificationScope(
        FailureNotification notification, @Nullable FailureNotificationScope previous) {
      this.notification = notification;
      this.previous = previous;
    }
  }

  private static final class ReadDispatch {
    private Channel[] channels = new Channel[4];
    private Object[] messages = new Object[4];
    private ResponseCallbackScope[] previousCallbacks = new ResponseCallbackScope[4];
    private int depth;

    private void push(
        Channel channel, Object msg, @Nullable ResponseCallbackScope previousCallback) {
      if (depth == channels.length) {
        channels = Arrays.copyOf(channels, depth * 2);
        messages = Arrays.copyOf(messages, depth * 2);
        previousCallbacks = Arrays.copyOf(previousCallbacks, depth * 2);
      }
      channels[depth] = channel;
      messages[depth] = msg;
      previousCallbacks[depth] = previousCallback;
      depth++;
    }

    private void clearTo(int previousDepth) {
      while (depth > previousDepth) {
        depth--;
        ResponseCallbackScope previousCallback = previousCallbacks[depth];
        if (previousCallback == null) {
          RESPONSE_CALLBACK.remove();
        } else {
          RESPONSE_CALLBACK.set(previousCallback);
        }
        channels[depth] = null;
        messages[depth] = null;
        previousCallbacks[depth] = null;
      }
    }
  }
}
