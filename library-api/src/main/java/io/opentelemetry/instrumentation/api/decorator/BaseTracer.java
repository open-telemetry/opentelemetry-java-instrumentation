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

package io.opentelemetry.instrumentation.api.decorator;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public abstract class BaseTracer {
  // Keeps track of the server span for the current trace.
  public static final Context.Key<Span> CONTEXT_SERVER_SPAN_KEY =
      Context.key("opentelemetry-trace-server-span-key");

  protected final Tracer tracer;

  public BaseTracer() {
    tracer = OpenTelemetry.getTracerProvider().get(getInstrumentationName(), getVersion());
  }

  public BaseTracer(Tracer tracer) {
    this.tracer = tracer;
  }

  public Span startSpan(Class<?> clazz) {
    String spanName = spanNameForClass(clazz);
    return startSpan(spanName);
  }

  public Span startSpan(String spanName) {
    return tracer.spanBuilder(spanName).startSpan();
  }

  public Span getCurrentSpan() {
    return tracer.getCurrentSpan();
  }

  /** Returns valid span of type SERVER from current context or <code>null</code> if not found. */
  // TODO when all decorator are replaced with tracers, make this method instance
  public static Span getCurrentServerSpan() {
    return CONTEXT_SERVER_SPAN_KEY.get(Context.current());
  }

  protected abstract String getInstrumentationName();

  protected String getVersion() {
    return null;
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  protected String spanNameForMethod(final Method method) {
    return spanNameForClass(method.getDeclaringClass()) + "." + method.getName();
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param method the method to get the name from, nullable
   * @return the span name from the class and method
   */
  protected String spanNameForMethod(final Class<?> clazz, final Method method) {
    return spanNameForMethod(clazz, null == method ? null : method.getName());
  }

  protected String spanNameForMethod(Class<?> cl, String methodName) {
    return spanNameForClass(cl) + "." + methodName;
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   */
  protected String spanNameForClass(final Class<?> clazz) {
    if (!clazz.isAnonymousClass()) {
      return clazz.getSimpleName();
    }
    String className = clazz.getName();
    if (clazz.getPackage() != null) {
      String pkgName = clazz.getPackage().getName();
      if (!pkgName.isEmpty()) {
        className = clazz.getName().replace(pkgName, "").substring(1);
      }
    }
    return className;
  }

  public void end(Span span) {
    span.end();
  }

  public void endExceptionally(Span span, Throwable throwable) {
    onError(span, unwrapThrowable(throwable));
    span.setStatus(Status.INTERNAL);
    end(span);
  }

  protected void onError(final Span span, final Throwable throwable) {
    addThrowable(span, throwable);
  }

  protected Throwable unwrapThrowable(Throwable throwable) {
    return throwable instanceof ExecutionException ? throwable.getCause() : throwable;
  }

  public void addThrowable(final Span span, final Throwable throwable) {
    span.recordException(throwable);
  }

  public static void onPeerConnection(final Span span, final InetSocketAddress remoteConnection) {
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
  }

  public static void onPeerConnection(final Span span, final InetAddress remoteAddress) {
    setPeer(span, remoteAddress.getHostName(), remoteAddress.getHostAddress());
  }

  public static void setPeer(final Span span, String peerName, String peerIp) {
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

  protected static String mapToPeer(String endpoint) {
    if (endpoint == null) {
      return null;
    }

    return Config.get().getEndpointPeerServiceMapping().get(endpoint);
  }
}
