/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Collection;
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

  /**
   * Lazily-cached MethodHandles for Dubbo Triple protocol's HttpRequest interface. Resolved once
   * from the runtime class to avoid per-call reflection overhead. The HttpRequest class (from
   * dubbo-remoting-http12) is not available at compile time against Dubbo 2.7.
   */
  private static final class HttpRequestAccess {
    private static final Object UNAVAILABLE = new Object();
    private static volatile Object instance;

    final MethodHandle uri;
    final MethodHandle header;
    final MethodHandle headerNames;
    final MethodHandle remoteAddr;
    final MethodHandle remotePort;
    final MethodHandle localAddr;
    final MethodHandle localPort;

    private HttpRequestAccess(Class<?> clazz) throws ReflectiveOperationException {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      uri = lookup.unreflect(clazz.getMethod("uri"));
      header = lookup.unreflect(clazz.getMethod("header", CharSequence.class));
      headerNames = lookup.unreflect(clazz.getMethod("headerNames"));
      remoteAddr = lookup.unreflect(clazz.getMethod("remoteAddr"));
      remotePort = lookup.unreflect(clazz.getMethod("remotePort"));
      localAddr = lookup.unreflect(clazz.getMethod("localAddr"));
      localPort = lookup.unreflect(clazz.getMethod("localPort"));
    }

    @Nullable
    static HttpRequestAccess resolve(Object requestObj) {
      Object ref = instance;
      if (ref == null) {
        try {
          ref = new HttpRequestAccess(requestObj.getClass());
        } catch (Throwable t) {
          ref = UNAVAILABLE;
        }
        instance = ref;
      }
      return ref instanceof HttpRequestAccess ? (HttpRequestAccess) ref : null;
    }
  }

  private static final TextMapGetter<Object> HTTP_REQUEST_GETTER =
      new TextMapGetter<Object>() {
        @Override
        @SuppressWarnings("unchecked") // headerNames() returns Collection<String> at runtime
        public Iterable<String> keys(Object carrier) {
          HttpRequestAccess access = HttpRequestAccess.resolve(carrier);
          if (access == null) {
            return emptyList();
          }
          try {
            return (Collection<String>) access.headerNames.invoke(carrier);
          } catch (Throwable ignored) {
            return emptyList();
          }
        }

        @Override
        @Nullable
        public String get(@Nullable Object carrier, String key) {
          if (carrier == null) {
            return null;
          }
          HttpRequestAccess access = HttpRequestAccess.resolve(carrier);
          if (access == null) {
            return null;
          }
          try {
            return (String) access.header.invoke(carrier, (CharSequence) key);
          } catch (Throwable ignored) {
            return null;
          }
        }
      };

  // "_otel" prefix avoids collisions with application-level Dubbo attachments;
  // "unknown_svc" = unknown service; used to deduplicate when getInvoker() is called
  // multiple times for a single request.
  private static final String DEDUP_ATTACHMENT_KEY = "_otel_unknown_svc_span";

  /**
   * Creates an unknown service span when {@code DubboProtocol.getInvoker()} throws. This handles
   * the Dubbo protocol (binary) case where decode succeeds past {@code
   * PermittedSerializationKeeper} (older Dubbo versions without the check, or generic invocations
   * that pass) but the invoker lookup fails. Trace context is extracted from invocation
   * attachments, so the resulting span is linked to the client span (1 trace).
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
   * handles the Dubbo protocol (binary) case where {@code PermittedSerializationKeeper} rejects the
   * request during decode because the service is not registered. This happens in newer Dubbo
   * versions that enforce serialization security checks.
   *
   * <p>Note: trace context is unavailable in this path because in the Dubbo protocol wire format,
   * attachments (which carry traceparent) are serialized after parameter values and haven't been
   * read when the decode fails. The resulting span will be a root span (disconnected trace).
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

  /**
   * Creates an unknown service span for the Dubbo Triple protocol (gRPC over HTTP/2). Unlike the
   * Dubbo protocol (binary), Triple transmits trace context as HTTP/2 headers, so the parent
   * context is always available — resulting in a single trace.
   *
   * <p>Uses cached MethodHandles to access the HttpRequest object because Triple classes (from
   * dubbo-rpc-triple / dubbo-remoting-http12) are not available at compile time against Dubbo 2.7.
   *
   * @param requestObj the HttpRequest object from GrpcRequestHandlerMapping.getRequestHandler()
   * @param throwable the exception thrown (typically HttpStatusException with 404)
   * @param startTimeMillis timestamp when the method was entered
   */
  @SuppressWarnings("deprecation") // Dubbo 2.7 API: setAttachment()
  public static void createUnknownServiceSpanFromTriple(
      Object requestObj, Throwable throwable, long startTimeMillis) {

    if (DubboSingletons.SERVER_INSTRUMENTER == null) {
      return;
    }

    HttpRequestAccess access = HttpRequestAccess.resolve(requestObj);
    if (access == null) {
      return;
    }

    String uri;
    try {
      uri = (String) access.uri.invoke(requestObj);
    } catch (Throwable ignored) {
      return;
    }
    if (uri == null) {
      return;
    }

    String fullMethodName = parseFullMethodName(uri);

    RpcInvocation rpcInvocation = new RpcInvocation();
    String[] parts = parseServiceAndMethod(uri);
    rpcInvocation.setAttachment("path", parts[0]);
    rpcInvocation.setMethodName(parts[1]);

    InetSocketAddress remoteAddress = buildSocketAddress(access, requestObj, true);
    InetSocketAddress localAddress = buildSocketAddress(access, requestObj, false);

    DubboRequest request =
        DubboRequest.createForUnknownService(
            rpcInvocation, fullMethodName, remoteAddress, localAddress);

    Context parentContext =
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.root(), requestObj, HTTP_REQUEST_GETTER);

    startAndEndSpan(
        DubboSingletons.SERVER_INSTRUMENTER, parentContext, request, throwable, startTimeMillis);
  }

  /**
   * Parses a Triple/gRPC URI path into a full method name. Input format: {@code
   * /ServiceName/MethodName} Output format: {@code ServiceName/MethodName}
   */
  private static String parseFullMethodName(String uri) {
    if (uri.startsWith("/")) {
      return uri.substring(1);
    }
    return uri;
  }

  /**
   * Parses service name and method name from a Triple/gRPC URI path. Input: {@code
   * /com.example.Service/method} Output: {@code ["com.example.Service", "method"]}
   */
  private static String[] parseServiceAndMethod(String uri) {
    String path = uri.startsWith("/") ? uri.substring(1) : uri;
    int slash = path.lastIndexOf('/');
    if (slash > 0) {
      return new String[] {path.substring(0, slash), path.substring(slash + 1)};
    }
    return new String[] {path, "unknown"};
  }

  @Nullable
  private static InetSocketAddress buildSocketAddress(
      HttpRequestAccess access, Object requestObj, boolean remote) {
    try {
      MethodHandle addrHandle = remote ? access.remoteAddr : access.localAddr;
      MethodHandle portHandle = remote ? access.remotePort : access.localPort;
      String addr = (String) addrHandle.invoke(requestObj);
      int port = (int) portHandle.invoke(requestObj);
      if (addr != null && port > 0) {
        return new InetSocketAddress(addr, port);
      }
    } catch (Throwable ignored) {
      // address extraction is best-effort
    }
    return null;
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
