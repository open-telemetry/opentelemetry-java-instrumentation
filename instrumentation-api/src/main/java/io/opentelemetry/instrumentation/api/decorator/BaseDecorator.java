/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.decorator;

import static io.opentelemetry.OpenTelemetry.getPropagators;
import static io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils.ENDPOINT_PEER_SERVICE_MAPPING;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.StatusCode;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Deprecated
public abstract class BaseDecorator {

  private static final ClassValue<SpanNames> SPAN_NAMES =
      new ClassValue<SpanNames>() {
        @Override
        protected SpanNames computeValue(Class<?> type) {
          return new SpanNames(getClassName(type));
        }
      };

  protected BaseDecorator() {}

  public Span afterStart(Span span) {
    assert span != null;
    return span;
  }

  public Span beforeFinish(Span span) {
    assert span != null;
    return span;
  }

  public Span onError(Span span, Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      onComplete(span, StatusCode.ERROR, throwable);
    }
    return span;
  }

  public Span onComplete(Span span, StatusCode status, Throwable throwable) {
    assert span != null;
    span.setStatus(status);
    if (throwable != null) {
      addThrowable(
          span, throwable instanceof ExecutionException ? throwable.getCause() : throwable);
    }
    return span;
  }

  public Span onPeerConnection(Span span, InetSocketAddress remoteConnection) {
    assert span != null;
    if (remoteConnection != null) {
      InetAddress remoteAddress = remoteConnection.getAddress();
      if (remoteAddress != null) {
        onPeerConnection(span, remoteAddress);
      } else {
        // Failed DNS lookup, the host string is the name.
        setPeer(span, remoteConnection.getHostString(), null);
      }
      span.setAttribute(SemanticAttributes.NET_PEER_PORT, (long) remoteConnection.getPort());
    }
    return span;
  }

  public Span onPeerConnection(Span span, InetAddress remoteAddress) {
    assert span != null;
    setPeer(span, remoteAddress.getHostName(), remoteAddress.getHostAddress());
    return span;
  }

  public static void setPeer(Span span, String peerName, String peerIp) {
    assert span != null;
    if (peerName != null && !peerName.equals(peerIp)) {
      span.setAttribute(SemanticAttributes.NET_PEER_NAME, peerName);
    }
    if (peerIp != null) {
      span.setAttribute(SemanticAttributes.NET_PEER_IP, peerIp);
    }
    String peerService = mapToPeer(peerName);
    if (peerService == null) {
      peerService = mapToPeer(peerIp);
    }
    if (peerService != null) {
      span.setAttribute(SemanticAttributes.PEER_SERVICE, peerService);
    }
  }

  public static void addThrowable(Span span, Throwable throwable) {
    span.recordException(throwable);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  public String spanNameForMethod(Method method) {
    return spanNameForMethod(method.getDeclaringClass(), method);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param method the method to get the name from, nullable
   * @return the span name from the class and method
   */
  public String spanNameForMethod(Class<?> clazz, Method method) {
    return spanNameForMethod(clazz, null == method ? null : method.getName());
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param methodName the name of the method to get the name from, nullable
   * @return the span name from the class and method
   */
  public String spanNameForMethod(Class<?> clazz, String methodName) {
    SpanNames cn = SPAN_NAMES.get(clazz);
    return null == methodName ? cn.getClassName() : cn.getSpanName(methodName);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   */
  public String spanNameForClass(Class<?> clazz) {
    String simpleName = clazz.getSimpleName();
    return simpleName.isEmpty() ? SPAN_NAMES.get(clazz).getClassName() : simpleName;
  }

  private static class SpanNames {
    private final String className;
    private final ConcurrentHashMap<String, String> spanNames = new ConcurrentHashMap<>(1);

    private SpanNames(String className) {
      this.className = className;
    }

    public String getClassName() {
      return className;
    }

    public String getSpanName(String methodName) {
      String spanName = spanNames.get(methodName);
      if (null == spanName) {
        spanName = className + "." + methodName;
        spanNames.putIfAbsent(methodName, spanName);
      }
      return spanName;
    }
  }

  private static String getClassName(Class<?> clazz) {
    String simpleName = clazz.getSimpleName();
    if (simpleName.isEmpty()) {
      String name = clazz.getName();
      int start = name.lastIndexOf('.');
      return name.substring(start + 1);
    }
    return simpleName;
  }

  public static <C> Context extract(C carrier, TextMapPropagator.Getter<C> getter) {
    // TODO add context leak debug

    // Using Context.ROOT here may be quite unexpected, but the reason is simple.
    // We want either span context extracted from the carrier or invalid one.
    // We DO NOT want any span context potentially lingering in the current context.
    return getPropagators().getTextMapPropagator().extract(Context.root(), carrier, getter);
  }

  protected static String mapToPeer(String endpoint) {
    if (endpoint == null) {
      return null;
    }

    return ENDPOINT_PEER_SERVICE_MAPPING.get(endpoint);
  }
}
