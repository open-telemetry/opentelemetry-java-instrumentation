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
package io.opentelemetry.auto.bootstrap.instrumentation.decorator;

import static io.opentelemetry.OpenTelemetry.getPropagators;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;

import io.grpc.Context;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Status;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

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
      span.setStatus(Status.UNKNOWN);
      addThrowable(
          span, throwable instanceof ExecutionException ? throwable.getCause() : throwable);
    }
    return span;
  }

  public Span onPeerConnection(final Span span, final InetSocketAddress remoteConnection) {
    assert span != null;
    if (remoteConnection != null) {
      onPeerConnection(span, remoteConnection.getAddress());

      span.setAttribute(MoreTags.NET_PEER_PORT, remoteConnection.getPort());
    }
    return span;
  }

  public Span onPeerConnection(final Span span, final InetAddress remoteAddress) {
    assert span != null;
    if (remoteAddress != null) {
      span.setAttribute(MoreTags.NET_PEER_NAME, remoteAddress.getHostName());
      span.setAttribute(MoreTags.NET_PEER_IP, remoteAddress.getHostAddress());
    }
    return span;
  }

  public static void addThrowable(final Span span, final Throwable throwable) {
    span.setAttribute(MoreTags.ERROR_MSG, throwable.getMessage());
    span.setAttribute(MoreTags.ERROR_TYPE, throwable.getClass().getName());

    final StringWriter errorString = new StringWriter();
    throwable.printStackTrace(new PrintWriter(errorString));
    span.setAttribute(MoreTags.ERROR_STACK, errorString.toString());
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
    final Context context =
        getPropagators().getHttpTextFormat().extract(Context.current(), carrier, getter);
    final Span span = getSpan(context);
    return span.getContext();
  }
}
