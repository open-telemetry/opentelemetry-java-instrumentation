/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequest;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpResponse;

final class NettyClientRequestAndContexts {

  private static final VirtualField<Channel, NettyClientRequestAndContexts> REQUEST_AND_CONTEXTS =
      VirtualField.find(Channel.class, NettyClientRequestAndContexts.class);

  private final Queue<RequestAndContexts> requests = new ArrayDeque<>();
  private boolean failed;
  private boolean writing;
  @Nullable private RequestAndContexts response;

  static NettyClientRequestAndContexts getOrCreate(Channel channel) {
    synchronized (channel) {
      NettyClientRequestAndContexts contexts = REQUEST_AND_CONTEXTS.get(channel);
      if (contexts == null) {
        contexts = new NettyClientRequestAndContexts();
        REQUEST_AND_CONTEXTS.set(channel, contexts);
      }
      return contexts;
    }
  }

  @Nullable
  static NettyClientRequestAndContexts get(Channel channel) {
    return REQUEST_AND_CONTEXTS.get(channel);
  }

  synchronized boolean add(RequestAndContexts request) {
    if (failed) {
      return false;
    }
    requests.add(request);
    return true;
  }

  synchronized boolean isFailed() {
    return failed;
  }

  synchronized boolean beginWrite() {
    if (failed || writing) {
      return false;
    }
    writing = true;
    return true;
  }

  synchronized void endWrite() {
    writing = false;
  }

  @Nullable
  synchronized RequestAndContexts peek() {
    return requests.peek();
  }

  @Nullable
  synchronized RequestAndContexts poll(boolean chunked) {
    RequestAndContexts request = requests.poll();
    response = chunked ? request : null;
    return request;
  }

  @Nullable
  synchronized RequestAndContexts response(boolean last) {
    RequestAndContexts request = response;
    if (last) {
      response = null;
    }
    return request;
  }

  void fail(Throwable error) {
    List<RequestAndContexts> pending;
    synchronized (this) {
      // A failed write may have reached the wire partially. Correlation is no longer reliable.
      failed = true;
      pending = new ArrayList<>(requests);
      requests.clear();
      response = null;
    }
    for (RequestAndContexts request : pending) {
      request.end(null, error);
    }
  }

  static final class RequestAndContexts {
    final Context parentContext;
    @Nullable final Context context;
    private final NettyRequest request;
    private final AtomicBoolean ended = new AtomicBoolean();

    RequestAndContexts(Context parentContext, @Nullable Context context, NettyRequest request) {
      this.parentContext = parentContext;
      this.context = context;
      this.request = request;
    }

    void end(@Nullable HttpResponse response, @Nullable Throwable error) {
      if (context != null && ended.compareAndSet(false, true)) {
        instrumenter()
            .end(context, request, response, NettyErrorHolder.getOrDefault(context, error));
      }
    }

    void closed() {
      end(null, new ClosedChannelException());
    }
  }
}
