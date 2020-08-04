/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.library.api.decorator;

import static io.opentelemetry.OpenTelemetry.getPropagators;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;

import io.grpc.Context;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.instrumentation.library.api.config.Config;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Status;
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

  public Span afterStart(final Span span) {
    assert span != null;
    return span;
  }

  public Span beforeFinish(final Span span) {
    assert span != null;
    return span;
  }

  public Span onError(final Span span, final Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      onComplete(span, Status.UNKNOWN, throwable);
    }
    return span;
  }

  public Span onComplete(Span span, Status status, Throwable throwable) {
    assert span != null;
    span.setStatus(status);
    if (throwable != null) {
      addThrowable(
          span, throwable instanceof ExecutionException ? throwable.getCause() : throwable);
    }
    return span;
  }

  public Span onPeerConnection(final Span span, final InetSocketAddress remoteConnection) {
    assert span != null;
    if (remoteConnection != null) {
      InetAddress remoteAddress = remoteConnection.getAddress();
      if (remoteAddress != null) {
        onPeerConnection(span, remoteAddress);
      } else {
        // Failed DNS lookup, the host string is the name.
        setPeer(span, remoteConnection.getHostString(), null);
      }
      span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), remoteConnection.getPort());
    }
    return span;
  }

  public Span onPeerConnection(final Span span, final InetAddress remoteAddress) {
    assert span != null;
    setPeer(span, remoteAddress.getHostName(), remoteAddress.getHostAddress());
    return span;
  }

  public static void setPeer(final Span span, String peerName, String peerIp) {
    assert span != null;
    if (peerName != null && !peerName.equals(peerIp)) {
      SemanticAttributes.NET_PEER_NAME.set(span, peerName);
    }
    if (peerIp != null) {
      SemanticAttributes.NET_PEER_IP.set(span, peerIp);
    }
    String peerService = mapToPeer(peerName);
    if (peerService == null) {
      peerService = mapToPeer(peerIp);
    }
    if (peerService != null) {
      SemanticAttributes.PEER_SERVICE.set(span, peerService);
    }
  }

  public static void addThrowable(final Span span, final Throwable throwable) {
    span.recordException(throwable);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  public String spanNameForMethod(final Method method) {
    return spanNameForMethod(method.getDeclaringClass(), method);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param method the method to get the name from, nullable
   * @return the span name from the class and method
   */
  public String spanNameForMethod(final Class<?> clazz, final Method method) {
    return spanNameForMethod(clazz, null == method ? null : method.getName());
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param methodName the name of the method to get the name from, nullable
   * @return the span name from the class and method
   */
  public String spanNameForMethod(final Class<?> clazz, final String methodName) {
    SpanNames cn = SPAN_NAMES.get(clazz);
    return null == methodName ? cn.getClassName() : cn.getSpanName(methodName);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   */
  public String spanNameForClass(final Class<?> clazz) {
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

  public static <C> SpanContext extract(final C carrier, final HttpTextFormat.Getter<C> getter) {
    Context context =
        getPropagators().getHttpTextFormat().extract(Context.current(), carrier, getter);
    Span span = getSpan(context);
    return span.getContext();
  }

  protected static String mapToPeer(String endpoint) {
    if (endpoint == null) {
      return null;
    }

    return Config.get().getEndpointPeerServiceMapping().get(endpoint);
  }
}
