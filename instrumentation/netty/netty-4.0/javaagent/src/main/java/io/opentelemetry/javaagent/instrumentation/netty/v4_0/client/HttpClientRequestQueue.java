/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyClientSingletons.instrumenter;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Request state shared by the separately installed outbound and inbound handlers. */
public final class HttpClientRequestQueue {
  private static final AttributeKey<HttpClientRequestQueue> KEY =
      AttributeKeys.attributeKey(AttributeKeys.class.getName() + ".http-client-requests");

  private final ArrayDeque<Request> requests = new ArrayDeque<>();
  private boolean failed;
  @Nullable private Throwable failure;
  private boolean informational;
  @Nullable private HttpResponse response;

  public static HttpClientRequestQueue get(Channel channel) {
    HttpClientRequestQueue queue = channel.attr(KEY).get();
    if (queue == null) {
      HttpClientRequestQueue newQueue = new HttpClientRequestQueue();
      HttpClientRequestQueue previous = channel.attr(KEY).setIfAbsent(newQueue);
      queue = previous == null ? newQueue : previous;
    }
    return queue;
  }

  public static void failIfPresent(Channel channel, Throwable cause) {
    HttpClientRequestQueue queue = channel.attr(KEY).get();
    if (queue != null) {
      queue.fail(cause);
    }
  }

  private HttpClientRequestQueue() {}

  synchronized boolean isFailed() {
    return failed;
  }

  void add(Request request) {
    Throwable failure;
    synchronized (this) {
      if (!failed) {
        requests.add(request);
        return;
      }
      failure = this.failure;
    }
    request.end(null, failure);
  }

  @Nullable
  synchronized Request peek() {
    return requests.peek();
  }

  synchronized void response(HttpResponse response) {
    if (failed) {
      return;
    }
    int status = response.getStatus().code();
    informational = status >= 100 && status < 200 && status != 101;
    if (!informational) {
      this.response = response;
    }
  }

  void endResponse() {
    Request request;
    HttpResponse response;
    synchronized (this) {
      if (informational) {
        informational = false;
        return;
      }
      request = requests.poll();
      response = this.response;
      this.response = null;
    }
    if (request != null) {
      request.end(response, null);
    }
    if (response != null && response.getStatus().code() == 101) {
      // The connection no longer carries HTTP responses, including for other queued requests.
      fail(new ClosedChannelException());
    }
  }

  public void fail(Throwable cause) {
    List<Request> pending;
    synchronized (this) {
      // A failed write may have sent part or all of the request. There is no safe way to pair
      // subsequent responses, so stop tracing this connection rather than shifting the FIFO.
      failed = true;
      failure = cause;
      pending = new ArrayList<>(requests);
      requests.clear();
      response = null;
      informational = false;
    }
    for (Request request : pending) {
      request.end(null, cause);
    }
  }

  static final class Request {
    final NettyCommonRequest request;
    final Context parentContext;
    @Nullable final Context context;
    private boolean ended;

    Request(NettyCommonRequest request, Context parentContext, @Nullable Context context) {
      this.request = request;
      this.parentContext = parentContext;
      this.context = context;
    }

    void end(@Nullable HttpResponse response, @Nullable Throwable cause) {
      synchronized (this) {
        if (ended) {
          return;
        }
        ended = true;
      }
      if (context != null) {
        instrumenter().end(context, request, response, cause);
      }
    }
  }
}
