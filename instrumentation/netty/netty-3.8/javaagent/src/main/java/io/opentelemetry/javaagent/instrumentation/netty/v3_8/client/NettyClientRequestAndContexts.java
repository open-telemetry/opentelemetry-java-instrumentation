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
  @Nullable private Write write;
  private boolean failed;
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

  synchronized boolean add(RequestAndContexts request, Write write) {
    if (failed) {
      return false;
    }
    requests.add(request);
    write.request = request;
    return true;
  }

  synchronized boolean isFailed() {
    return failed;
  }

  @Nullable
  synchronized Write beginWrite(boolean requestWrite) {
    if (failed) {
      return null;
    }
    if (write != null && (write.request == null || !write.request.responseComplete)) {
      return null;
    }
    Write newWrite = new Write();
    if (!requestWrite) {
      newWrite.request = requests.peek();
    }
    write = newWrite;
    return newWrite;
  }

  synchronized void endWrite(Write write) {
    if (this.write == write) {
      this.write = null;
    }
  }

  @Nullable
  synchronized RequestAndContexts peek() {
    return requests.peek();
  }

  @Nullable
  synchronized RequestAndContexts poll(boolean chunked) {
    RequestAndContexts request = requests.poll();
    if (request != null && !chunked) {
      request.responseComplete = true;
    }
    response = chunked ? request : null;
    return request;
  }

  @Nullable
  synchronized RequestAndContexts response(boolean last) {
    RequestAndContexts request = response;
    if (last) {
      response = null;
      if (request != null) {
        request.responseComplete = true;
      }
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

  static final class Write {
    @Nullable RequestAndContexts request;
  }

  static final class RequestAndContexts {
    final Context parentContext;
    @Nullable final Context context;
    private final NettyRequest request;
    private final AtomicBoolean ended = new AtomicBoolean();
    private boolean responseComplete;

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
