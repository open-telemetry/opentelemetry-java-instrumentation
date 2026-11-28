/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.common.v2_7;

import static org.apache.dubbo.common.Constants.BIND_IP_KEY;
import static org.apache.dubbo.common.Constants.BIND_PORT_KEY;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.apachedubbo.common.v2_7.internal.DubboRegistryUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerEndpointUtil;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.service.GenericService;

@AutoValue
public abstract class DubboRequest {

  private static final ClassValue<Set<String>> serviceMethods =
      new ClassValue<Set<String>>() {
        @Override
        protected Set<String> computeValue(Class<?> service) {
          Set<String> names = new HashSet<>();
          for (Method method : service.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
              names.add(method.getName());
            }
          }
          return names;
        }
      };

  @SuppressWarnings("deprecation") // RpcContext.getContext()
  static DubboRequest create(RpcInvocation invocation, RpcContext context) {
    // In dubbo 3 RpcContext delegates to a ThreadLocal context. We copy the url, remote address,
    // and registry address here to ensure we can access them from the thread that ends the span.
    return new AutoValue_DubboRequest(
        invocation,
        context,
        context.getUrl(),
        context.getRemoteAddress(),
        context.getLocalAddress(),
        DubboRegistryUtil.extractRegistryAddress(invocation),
        null,
        null);
  }

  static DubboRequest createForInvoker(
      RpcInvocation invocation, RpcContext context, Invoker<?> invoker) {
    URL url = invoker.getUrl();
    InetSocketAddress localAddress = context.getLocalAddress();
    if (localAddress == null) {
      String host = url.getParameter(BIND_IP_KEY, url.getHost());
      int port = url.getParameter(BIND_PORT_KEY, url.getPort());
      String literal =
          host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
      // Only pass validated, unscoped numeric literals to the resolving constructor.
      if (ServerEndpointUtil.isIpv4Literal(literal)
          || (literal.indexOf('%') < 0 && ServerEndpointUtil.isIpv6Literal(literal))) {
        localAddress = new InetSocketAddress(literal, port);
      } else {
        localAddress = InetSocketAddress.createUnresolved(host, port);
      }
    }
    Class<?> service = invoker.getInterface();
    String method = invocation.getMethodName();
    String originalFullMethodName = null;
    Object[] arguments = invocation.getArguments();
    boolean genericDispatch = "$invoke".equals(method) || "$invokeAsync".equals(method);
    // GenericFilter has not necessarily converted the invocation yet.
    if (!GenericService.class.isAssignableFrom(service)
        && genericDispatch
        && arguments != null
        && arguments.length == 3
        && arguments[0] instanceof String) {
      method = ((String) arguments[0]).trim();
      if (!serviceMethods.get(service).contains(method)) {
        originalFullMethodName = service.getName() + "/" + method;
      }
    } else if (!genericDispatch
        && !"$echo".equals(method)
        && !serviceMethods.get(service).contains(method)) {
      originalFullMethodName = service.getName() + "/" + method;
    }
    return new AutoValue_DubboRequest(
        invocation,
        context,
        url,
        context.getRemoteAddress(),
        localAddress,
        DubboRegistryUtil.extractRegistryAddress(invocation),
        originalFullMethodName == null
            ? service.getName() + "/" + method
            : DubboRpcAttributesGetter.UNKNOWN_METHOD_SPAN_NAME,
        originalFullMethodName);
  }

  /** Creates a request for javaagent instrumentation when no service invoker is available. */
  public static DubboRequest createForUnknownService(
      RpcInvocation invocation,
      String originalFullMethodName,
      @Nullable InetSocketAddress remoteAddress) {
    return new AutoValue_DubboRequest(
        invocation, null, null, remoteAddress, null, null, null, originalFullMethodName);
  }

  abstract RpcInvocation invocation();

  @Nullable
  public abstract RpcContext context();

  @Nullable
  public abstract URL url();

  @Nullable
  public abstract InetSocketAddress remoteAddress();

  @Nullable
  public abstract InetSocketAddress localAddress();

  @Nullable
  public abstract String registryAddress();

  @Nullable
  abstract String rpcMethod();

  @Nullable
  abstract String originalFullMethodName();

  boolean isUnknownService() {
    return originalFullMethodName() != null && rpcMethod() == null;
  }
}
