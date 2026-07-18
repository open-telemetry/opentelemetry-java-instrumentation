/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7.DubboSingletons.serverInstrumenter;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
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
   * Creates an unknown service span when {@code DubboProtocol.getInvoker()} throws because the
   * requested service is not registered in the exporter map. This handles the Dubbo protocol
   * (binary) case where decode succeeds past {@code PermittedSerializationKeeper} (older Dubbo
   * versions without the check, or generic invocations that pass) but the invoker lookup fails.
   * Trace context is extracted from invocation attachments, so the resulting span is linked to the
   * client span (1 trace).
   *
   * <p>Only {@code RemotingException} with "Not found exported service" triggers span creation;
   * other getInvoker failures are not unknown-service errors and are ignored.
   */
  @SuppressWarnings("deprecation") // Dubbo 2.7 API: getAttachments()
  public static void createUnknownServiceSpan(
      RpcInvocation rpcInvocation,
      @Nullable InetSocketAddress remoteAddress,
      Throwable throwable,
      long startTimeMillis) {

    if (serverInstrumenter() == null
        || !isUnknownServiceInvokerFailure(throwable)
        || isAlreadyRecorded(rpcInvocation)) {
      return;
    }

    DubboRequest request =
        DubboRequest.createForUnknownService(
            rpcInvocation, buildOriginalFullMethodName(rpcInvocation), remoteAddress);

    Context parentContext =
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.root(), rpcInvocation.getAttachments(), MAP_GETTER);

    startAndEndSpan(serverInstrumenter(), parentContext, request, throwable, startTimeMillis);
  }

  /**
   * Creates an unknown service span when {@code DecodeableRpcInvocation.decode()} throws because
   * {@code PermittedSerializationKeeper} rejects the request for an unregistered service. This
   * happens in newer Dubbo versions that enforce serialization security checks.
   *
   * <p>Only {@code IOException} whose message contains "invocation rejected" (the signature of
   * {@code PermittedSerializationKeeper}) triggers span creation. Other decode failures (malformed
   * streams, unsupported serialization, argument deserialization for registered services, etc.) are
   * not unknown-service errors and are ignored.
   *
   * <p>Note: trace context is unavailable in this path because in the Dubbo protocol wire format,
   * attachments (which carry traceparent) are serialized after parameter values and haven't been
   * read when the decode fails. The resulting span will be a root span (disconnected trace).
   */
  public static void createUnknownServiceSpanFromDecode(
      RpcInvocation rpcInvocation, Object channelObj, Throwable throwable, long startTimeMillis) {

    if (serverInstrumenter() == null
        || !isUnknownServiceDecodeFailure(throwable)
        || isAlreadyRecorded(rpcInvocation)) {
      return;
    }

    InetSocketAddress remoteAddress = null;
    try {
      Channel channel = (Channel) channelObj;
      remoteAddress = channel.getRemoteAddress();
    } catch (Throwable ignored) {
      // channel type may not match in some versions
    }

    DubboRequest request =
        DubboRequest.createForUnknownService(
            rpcInvocation, buildOriginalFullMethodName(rpcInvocation), remoteAddress);

    startAndEndSpan(serverInstrumenter(), Context.root(), request, throwable, startTimeMillis);
  }

  /**
   * Creates an unknown service span for the Dubbo Triple protocol (gRPC over HTTP/2). Unlike the
   * Dubbo protocol (binary), Triple transmits trace context as HTTP/2 headers, so the parent
   * context is always available — resulting in a single trace.
   *
   * <p>Only {@code HttpStatusException} with status 404 (the Dubbo Triple not-found outcome)
   * triggers span creation. Other Triple failures (e.g., descriptor/codec construction for
   * recognized methods) are not unknown-service errors and are ignored.
   *
   * <p>Uses cached MethodHandles to access the HttpRequest object because Triple classes (from
   * dubbo-rpc-triple / dubbo-remoting-http12) are not available at compile time against Dubbo 2.7.
   *
   * @param requestObj the HttpRequest object from GrpcRequestHandlerMapping.getRequestHandler()
   * @param throwable the exception thrown (HttpStatusException with 404 for not-found)
   * @param startTimeMillis timestamp when the method was entered
   */
  @SuppressWarnings("deprecation") // Dubbo 2.7 API: setAttachment()
  public static void createUnknownServiceSpanFromTriple(
      Object requestObj, Throwable throwable, long startTimeMillis) {

    if (serverInstrumenter() == null || !isTripleNotFoundFailure(throwable)) {
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

    InetSocketAddress remoteAddress = buildRemoteSocketAddress(access, requestObj);

    DubboRequest request =
        DubboRequest.createForUnknownService(rpcInvocation, fullMethodName, remoteAddress);

    Context parentContext =
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.root(), requestObj, HTTP_REQUEST_GETTER);

    startAndEndSpan(serverInstrumenter(), parentContext, request, throwable, startTimeMillis);
  }

  /**
   * Returns {@code true} if the throwable indicates that {@code PermittedSerializationKeeper}
   * rejected the request because the service is not registered.
   */
  static boolean isUnknownServiceDecodeFailure(Throwable throwable) {
    if (!(throwable instanceof IOException)) {
      return false;
    }
    String message = throwable.getMessage();
    return message != null && message.contains("invocation rejected");
  }

  /**
   * Returns {@code true} if the throwable indicates that {@code DubboProtocol.getInvoker()} could
   * not find the requested service in the exporter map.
   */
  static boolean isUnknownServiceInvokerFailure(Throwable throwable) {
    String message = throwable.getMessage();
    return message != null && message.contains("Not found exported service");
  }

  /**
   * Returns {@code true} if the throwable is a Dubbo Triple 404 (service not found). The class
   * check uses the name string because {@code HttpStatusException} is not available at compile time
   * against Dubbo 2.7.
   */
  static boolean isTripleNotFoundFailure(Throwable throwable) {
    if (!"org.apache.dubbo.remoting.http12.exception.HttpStatusException"
        .equals(throwable.getClass().getName())) {
      return false;
    }
    try {
      Method statusMethod = throwable.getClass().getMethod("getStatusCode");
      Object status = statusMethod.invoke(throwable);
      return status instanceof Integer && (Integer) status == 404;
    } catch (ReflectiveOperationException ignored) {
      return false;
    }
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
  private static InetSocketAddress buildRemoteSocketAddress(
      HttpRequestAccess access, Object requestObj) {
    try {
      String addr = (String) access.remoteAddr.invoke(requestObj);
      int port = (int) access.remotePort.invoke(requestObj);
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

  public static boolean isEnabled() {
    return serverInstrumenter() != null;
  }

  private DubboUnknownServiceHelper() {}

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

    private HttpRequestAccess(Class<?> clazz) throws ReflectiveOperationException {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      uri = lookup.unreflect(clazz.getMethod("uri"));
      header = lookup.unreflect(clazz.getMethod("header", CharSequence.class));
      headerNames = lookup.unreflect(clazz.getMethod("headerNames"));
      remoteAddr = lookup.unreflect(clazz.getMethod("remoteAddr"));
      remotePort = lookup.unreflect(clazz.getMethod("remotePort"));
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
}
