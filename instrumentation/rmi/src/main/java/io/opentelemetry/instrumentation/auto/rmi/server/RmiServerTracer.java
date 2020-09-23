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

package io.opentelemetry.instrumentation.auto.rmi.server;

import static io.opentelemetry.trace.Span.Kind.SERVER;

import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.instrumentation.api.tracer.RpcServerTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Builder;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;

public class RmiServerTracer extends RpcServerTracer {
  public static final RmiServerTracer TRACER = new RmiServerTracer();

  public Span startSpan(Method method, SpanContext parentContext) {
    String serviceName = method.getDeclaringClass().getName();
    String methodName = method.getName();

    Builder spanBuilder =
        tracer
            .spanBuilder(serviceName + "/" + methodName)
            .setSpanKind(SERVER)
            .setParent(parentContext);
    SemanticAttributes.RPC_SYSTEM.set(spanBuilder, "java_rmi");
    SemanticAttributes.RPC_SERVICE.set(spanBuilder, serviceName);
    SemanticAttributes.RPC_METHOD.set(spanBuilder, methodName);

    return spanBuilder.startSpan();
  }

  @Override
  protected Getter getGetter() {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.rmi";
  }
}
