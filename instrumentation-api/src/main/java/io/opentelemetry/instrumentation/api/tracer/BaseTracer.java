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

package io.opentelemetry.instrumentation.api.tracer;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import io.opentelemetry.trace.EndSpanOptions;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;

public abstract class BaseTracer {
  // Keeps track of the server span for the current trace.
  // TODO(anuraaga): Should probably be renamed to local root key since it could be a consumer span
  // or other non-server root.
  public static final Context.Key<Span> CONTEXT_SERVER_SPAN_KEY =
      Context.key("opentelemetry-trace-server-span-key");

  // Keeps track of the client span in a subtree corresponding to a client request.
  // Visible for testing
  public static final Context.Key<Span> CONTEXT_CLIENT_SPAN_KEY =
      Context.key("opentelemetry-trace-auto-client-span-key");

  protected final Tracer tracer;

  public BaseTracer() {
    tracer = OpenTelemetry.getTracer(getInstrumentationName(), getVersion());
  }

  public BaseTracer(Tracer tracer) {
    this.tracer = tracer;
  }

  public Span startSpan(Class<?> clazz) {
    String spanName = spanNameForClass(clazz);
    return startSpan(spanName, Kind.INTERNAL);
  }

  public Span startSpan(Method method) {
    String spanName = spanNameForMethod(method);
    return startSpan(spanName, Kind.INTERNAL);
  }

  public Span startSpan(String spanName, Kind kind) {
    return tracer.spanBuilder(spanName).setSpanKind(kind).startSpan();
  }

  public Span getCurrentSpan() {
    return tracer.getCurrentSpan();
  }

  protected abstract String getInstrumentationName();

  protected String getVersion() {
    return InstrumentationVersion.VERSION;
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  protected String spanNameForMethod(Method method) {
    return spanNameForClass(method.getDeclaringClass()) + "." + method.getName();
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param method the method to get the name from, nullable
   * @return the span name from the class and method
   */
  protected String spanNameForMethod(Class<?> clazz, Method method) {
    return spanNameForMethod(clazz, null == method ? null : method.getName());
  }

  protected String spanNameForMethod(Class<?> cl, String methodName) {
    return spanNameForClass(cl) + "." + methodName;
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   */
  protected String spanNameForClass(Class<?> clazz) {
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
    end(span, -1);
  }

  public void end(Span span, long endTimeNanos) {
    if (endTimeNanos > 0) {
      span.end(EndSpanOptions.builder().setEndTimestamp(endTimeNanos).build());
    } else {
      span.end();
    }
  }

  public void endExceptionally(Span span, Throwable throwable) {
    endExceptionally(span, throwable, -1);
  }

  public void endExceptionally(Span span, Throwable throwable, long endTimeNanos) {
    span.setStatus(Status.INTERNAL);
    onError(span, unwrapThrowable(throwable));
    end(span, endTimeNanos);
  }

  protected void onError(Span span, Throwable throwable) {
    addThrowable(span, throwable);
  }

  protected Throwable unwrapThrowable(Throwable throwable) {
    return throwable instanceof ExecutionException ? throwable.getCause() : throwable;
  }

  public void addThrowable(Span span, Throwable throwable) {
    span.recordException(throwable);
  }

  /** Returns valid span of type SERVER from current context or <code>null</code> if not found. */
  // TODO when all decorator are replaced with tracers, make this method instance
  public static Span getCurrentServerSpan() {
    return CONTEXT_SERVER_SPAN_KEY.get(Context.current());
  }
}
