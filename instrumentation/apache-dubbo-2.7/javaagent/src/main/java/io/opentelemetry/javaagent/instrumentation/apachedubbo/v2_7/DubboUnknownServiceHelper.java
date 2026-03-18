/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

public final class DubboUnknownServiceHelper {

  private static final TextMapGetter<Map<String, String>> MAP_GETTER =
      new TextMapGetter<Map<String, String>>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Override
        @Nullable
        public String get(@Nullable Map<String, String> carrier, String key) {
          return carrier != null ? carrier.get(key) : null;
        }
      };

  // "_otel" prefix avoids collisions with application-level Dubbo attachments;
  // "unknown_svc" = unknown service; used to deduplicate when getInvoker() is called
  // multiple times for a single request.
  private static final String DEDUP_ATTACHMENT_KEY = "_otel_unknown_svc_span";

  /**
   * Creates an unknown service span when {@code DubboProtocol.getInvoker()} throws. This handles
   * Dubbo 2.7 where decode succeeds but the invoker lookup fails. Trace context is extracted from
   * invocation attachments, so the resulting span is linked to the client span.
   */
  @SuppressWarnings("deprecation") // Dubbo 2.7 API: getAttachments()
  public static void createUnknownServiceSpan(
      RpcInvocation rpcInvocation,
      @Nullable InetSocketAddress remoteAddress,
      @Nullable InetSocketAddress localAddress,
      Throwable throwable,
      long startTimeMillis) {

    if (DubboSingletons.SERVER_INSTRUMENTER == null || isAlreadyRecorded(rpcInvocation)) {
      return;
    }

    DubboRequest request =
        DubboRequest.createForUnknownService(
            rpcInvocation, buildOriginalFullMethodName(rpcInvocation), remoteAddress, localAddress);

    Context parentContext =
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.root(), rpcInvocation.getAttachments(), MAP_GETTER);

    startAndEndSpan(
        DubboSingletons.SERVER_INSTRUMENTER, parentContext, request, throwable, startTimeMillis);
  }

  /**
   * Creates an unknown service span when {@code DecodeableRpcInvocation.decode()} throws. This
   * handles Dubbo 3.x (and 2.7.23+) where typed invocations to unknown services fail during the
   * decode phase before getInvoker() is ever called.
   *
   * <p>Note: trace context is unavailable in this path because Dubbo attachments (which carry
   * traceparent) are serialized after parameter values in the wire format and haven't been read
   * when the decode fails. The resulting span will be a root span.
   */
  public static void createUnknownServiceSpanFromDecode(
      RpcInvocation rpcInvocation, Object channelObj, Throwable throwable, long startTimeMillis) {

    if (DubboSingletons.SERVER_INSTRUMENTER == null || isAlreadyRecorded(rpcInvocation)) {
      return;
    }

    InetSocketAddress remoteAddress = null;
    InetSocketAddress localAddress = null;
    try {
      Channel channel = (Channel) channelObj;
      remoteAddress = channel.getRemoteAddress();
      localAddress = channel.getLocalAddress();
    } catch (Throwable ignored) {
      // channel type may not match in some versions
    }

    DubboRequest request =
        DubboRequest.createForUnknownService(
            rpcInvocation, buildOriginalFullMethodName(rpcInvocation), remoteAddress, localAddress);

    startAndEndSpan(
        DubboSingletons.SERVER_INSTRUMENTER, Context.root(), request, throwable, startTimeMillis);
  }

  @SuppressWarnings("deprecation") // Dubbo 2.7 API
  private static boolean isAlreadyRecorded(RpcInvocation rpcInvocation) {
    if (rpcInvocation.getAttachment(DEDUP_ATTACHMENT_KEY) != null) {
      return true;
    }
    rpcInvocation.setAttachment(DEDUP_ATTACHMENT_KEY, "true");
    return false;
  }

  @SuppressWarnings("deprecation") // Dubbo 2.7 API
  private static String buildOriginalFullMethodName(RpcInvocation rpcInvocation) {
    String path = rpcInvocation.getAttachment("path");
    String method = rpcInvocation.getMethodName();
    return (path != null ? path : "unknown") + "/" + (method != null ? method : "unknown");
  }

  private static void startAndEndSpan(
      Instrumenter<DubboRequest, Result> instrumenter,
      Context parentContext,
      DubboRequest request,
      Throwable throwable,
      long startTimeMillis) {
    if (instrumenter.shouldStart(parentContext, request)) {
      InstrumenterUtil.startAndEnd(
          instrumenter,
          parentContext,
          request,
          null,
          throwable,
          Instant.ofEpochMilli(startTimeMillis),
          Instant.now());
    }
  }

  private DubboUnknownServiceHelper() {}
}
