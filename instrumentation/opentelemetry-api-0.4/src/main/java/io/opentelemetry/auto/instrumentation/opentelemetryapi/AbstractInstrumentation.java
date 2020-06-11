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

import static java.util.Collections.singletonMap;

import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;

public abstract class AbstractInstrumentation extends Instrumenter.Default {
  public AbstractInstrumentation() {
    super("opentelemetry-api");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".context.ContextUtils",
      packageName + ".context.UnshadedScope",
      packageName + ".context.NoopScope",
      packageName + ".context.propagation.UnshadedContextPropagators",
      packageName + ".context.propagation.UnshadedHttpTextFormat",
      packageName + ".context.propagation.UnshadedHttpTextFormat$UnshadedSetter",
      packageName + ".context.propagation.UnshadedHttpTextFormat$UnshadedGetter",
      packageName + ".metrics.UnshadedBatchRecorder",
      packageName + ".metrics.UnshadedDoubleCounter",
      packageName + ".metrics.UnshadedDoubleCounter$BoundInstrument",
      packageName + ".metrics.UnshadedDoubleCounter$Builder",
      packageName + ".metrics.UnshadedDoubleSumObserver",
      packageName + ".metrics.UnshadedDoubleSumObserver$Builder",
      packageName + ".metrics.UnshadedDoubleSumObserver$ShadedResultDoubleSumObserver",
      packageName + ".metrics.UnshadedDoubleSumObserver$UnshadedResultDoubleSumObserver",
      packageName + ".metrics.UnshadedDoubleUpDownCounter",
      packageName + ".metrics.UnshadedDoubleUpDownCounter$BoundInstrument",
      packageName + ".metrics.UnshadedDoubleUpDownCounter$Builder",
      packageName + ".metrics.UnshadedDoubleUpDownSumObserver",
      packageName + ".metrics.UnshadedDoubleUpDownSumObserver$Builder",
      packageName + ".metrics.UnshadedDoubleUpDownSumObserver$ShadedResultDoubleUpDownSumObserver",
      packageName
          + ".metrics.UnshadedDoubleUpDownSumObserver$UnshadedResultDoubleUpDownSumObserver",
      packageName + ".metrics.UnshadedDoubleValueObserver",
      packageName + ".metrics.UnshadedDoubleValueObserver$Builder",
      packageName + ".metrics.UnshadedDoubleValueObserver$ShadedResultDoubleValueObserver",
      packageName + ".metrics.UnshadedDoubleValueObserver$UnshadedResultDoubleValueObserver",
      packageName + ".metrics.UnshadedDoubleValueRecorder",
      packageName + ".metrics.UnshadedDoubleValueRecorder$BoundInstrument",
      packageName + ".metrics.UnshadedDoubleValueRecorder$Builder",
      packageName + ".metrics.UnshadedLongCounter",
      packageName + ".metrics.UnshadedLongCounter$BoundInstrument",
      packageName + ".metrics.UnshadedLongCounter$Builder",
      packageName + ".metrics.UnshadedLongSumObserver",
      packageName + ".metrics.UnshadedLongSumObserver$Builder",
      packageName + ".metrics.UnshadedLongSumObserver$ShadedResultLongSumObserver",
      packageName + ".metrics.UnshadedLongSumObserver$UnshadedResultLongSumObserver",
      packageName + ".metrics.UnshadedLongUpDownCounter",
      packageName + ".metrics.UnshadedLongUpDownCounter$BoundInstrument",
      packageName + ".metrics.UnshadedLongUpDownCounter$Builder",
      packageName + ".metrics.UnshadedLongUpDownSumObserver",
      packageName + ".metrics.UnshadedLongUpDownSumObserver$Builder",
      packageName + ".metrics.UnshadedLongUpDownSumObserver$ShadedResultLongUpDownSumObserver",
      packageName + ".metrics.UnshadedLongUpDownSumObserver$UnshadedResultLongUpDownSumObserver",
      packageName + ".metrics.UnshadedLongValueObserver",
      packageName + ".metrics.UnshadedLongValueObserver$Builder",
      packageName + ".metrics.UnshadedLongValueObserver$ShadedResultLongValueObserver",
      packageName + ".metrics.UnshadedLongValueObserver$UnshadedResultLongValueObserver",
      packageName + ".metrics.UnshadedLongValueRecorder",
      packageName + ".metrics.UnshadedLongValueRecorder$BoundInstrument",
      packageName + ".metrics.UnshadedLongValueRecorder$Builder",
      packageName + ".metrics.UnshadedMeter",
      packageName + ".metrics.UnshadedMeterProvider",
      packageName + ".trace.Bridging",
      packageName + ".trace.Bridging$1",
      packageName + ".trace.TracingContextUtils",
      packageName + ".trace.UnshadedSpan",
      packageName + ".trace.UnshadedSpan$Builder",
      packageName + ".trace.UnshadedTracer",
      packageName + ".trace.UnshadedTracerProvider"
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("unshaded.io.grpc.Context", "io.grpc.Context");
  }
}
