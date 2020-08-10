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

package io.opentelemetry.auto.instrumentation.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.opentelemetryapi.context.propagation.UnshadedContextPropagators;
import io.opentelemetry.auto.instrumentation.opentelemetryapi.metrics.UnshadedMeterProvider;
import io.opentelemetry.auto.instrumentation.opentelemetryapi.trace.UnshadedTracerProvider;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import unshaded.io.grpc.Context;

@AutoService(Instrumenter.class)
public class OpenTelemetryApiInstrumentation extends AbstractInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("unshaded.io.opentelemetry.OpenTelemetry");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(isPublic()).and(named("getTracerProvider")).and(takesArguments(0)),
        OpenTelemetryApiInstrumentation.class.getName() + "$GetTracerProviderAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(named("getMeterProvider")).and(takesArguments(0)),
        OpenTelemetryApiInstrumentation.class.getName() + "$GetMeterProviderAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(named("getPropagators")).and(takesArguments(0)),
        OpenTelemetryApiInstrumentation.class.getName() + "$GetPropagatorsAdvice");
    return transformers;
  }

  public static class GetTracerProviderAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return(readOnly = false)
            unshaded.io.opentelemetry.trace.TracerProvider tracerProvider) {
      ContextStore<Context, io.grpc.Context> contextStore =
          InstrumentationContext.get(Context.class, io.grpc.Context.class);
      tracerProvider = new UnshadedTracerProvider(contextStore, tracerProvider);
    }
  }

  public static class GetMeterProviderAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return(readOnly = false)
            unshaded.io.opentelemetry.metrics.MeterProvider meterProvider) {
      meterProvider = new UnshadedMeterProvider();
    }
  }

  public static class GetPropagatorsAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return(readOnly = false)
            unshaded.io.opentelemetry.context.propagation.ContextPropagators contextPropagators) {
      ContextStore<Context, io.grpc.Context> contextStore =
          InstrumentationContext.get(Context.class, io.grpc.Context.class);
      contextPropagators = new UnshadedContextPropagators(contextStore);
    }
  }
}
