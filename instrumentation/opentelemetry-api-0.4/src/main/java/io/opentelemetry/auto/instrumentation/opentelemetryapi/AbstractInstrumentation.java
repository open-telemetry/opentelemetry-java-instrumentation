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
      packageName + ".metrics.UnshadedDoubleMeasure",
      packageName + ".metrics.UnshadedDoubleMeasure$BoundInstrument",
      packageName + ".metrics.UnshadedDoubleMeasure$Builder",
      packageName + ".metrics.UnshadedDoubleObserver",
      packageName + ".metrics.UnshadedDoubleObserver$Builder",
      packageName + ".metrics.UnshadedDoubleObserver$ShadedResultDoubleObserver",
      packageName + ".metrics.UnshadedDoubleObserver$UnshadedResultDoubleObserver",
      packageName + ".metrics.UnshadedLongCounter",
      packageName + ".metrics.UnshadedLongCounter$BoundInstrument",
      packageName + ".metrics.UnshadedLongCounter$Builder",
      packageName + ".metrics.UnshadedLongMeasure",
      packageName + ".metrics.UnshadedLongMeasure$BoundInstrument",
      packageName + ".metrics.UnshadedLongMeasure$Builder",
      packageName + ".metrics.UnshadedLongObserver",
      packageName + ".metrics.UnshadedLongObserver$Builder",
      packageName + ".metrics.UnshadedLongObserver$ShadedResultLongObserver",
      packageName + ".metrics.UnshadedLongObserver$UnshadedResultLongObserver",
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
