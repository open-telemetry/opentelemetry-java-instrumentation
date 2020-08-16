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

package io.opentelemetry.instrumentation.auto.opentelemetryapi;

import static java.util.Collections.singletonMap;

import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;

public abstract class AbstractInstrumentation extends Instrumenter.Default {
  public AbstractInstrumentation() {
    super("opentelemetry-api");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".context.ContextUtils",
      packageName + ".context.ApplicationScope",
      packageName + ".context.NoopScope",
      packageName + ".context.propagation.ApplicationContextPropagators",
      packageName + ".context.propagation.ApplicationHttpTextFormat",
      packageName + ".context.propagation.ApplicationHttpTextFormat$AgentSetter",
      packageName + ".context.propagation.ApplicationHttpTextFormat$AgentGetter",
      packageName + ".metrics.ApplicationBatchRecorder",
      packageName + ".metrics.ApplicationDoubleCounter",
      packageName + ".metrics.ApplicationDoubleCounter$BoundInstrument",
      packageName + ".metrics.ApplicationDoubleCounter$Builder",
      packageName + ".metrics.ApplicationDoubleSumObserver",
      packageName + ".metrics.ApplicationDoubleSumObserver$Builder",
      packageName + ".metrics.ApplicationDoubleSumObserver$AgentResultDoubleSumObserver",
      packageName + ".metrics.ApplicationDoubleSumObserver$ApplicationResultDoubleSumObserver",
      packageName + ".metrics.ApplicationDoubleUpDownCounter",
      packageName + ".metrics.ApplicationDoubleUpDownCounter$BoundInstrument",
      packageName + ".metrics.ApplicationDoubleUpDownCounter$Builder",
      packageName + ".metrics.ApplicationDoubleUpDownSumObserver",
      packageName + ".metrics.ApplicationDoubleUpDownSumObserver$Builder",
      packageName
          + ".metrics.ApplicationDoubleUpDownSumObserver$AgentResultDoubleUpDownSumObserver",
      packageName
          + ".metrics.ApplicationDoubleUpDownSumObserver$ApplicationResultDoubleUpDownSumObserver",
      packageName + ".metrics.ApplicationDoubleValueObserver",
      packageName + ".metrics.ApplicationDoubleValueObserver$Builder",
      packageName + ".metrics.ApplicationDoubleValueObserver$AgentResultDoubleValueObserver",
      packageName + ".metrics.ApplicationDoubleValueObserver$ApplicationResultDoubleValueObserver",
      packageName + ".metrics.ApplicationDoubleValueRecorder",
      packageName + ".metrics.ApplicationDoubleValueRecorder$BoundInstrument",
      packageName + ".metrics.ApplicationDoubleValueRecorder$Builder",
      packageName + ".metrics.ApplicationLongCounter",
      packageName + ".metrics.ApplicationLongCounter$BoundInstrument",
      packageName + ".metrics.ApplicationLongCounter$Builder",
      packageName + ".metrics.ApplicationLongSumObserver",
      packageName + ".metrics.ApplicationLongSumObserver$Builder",
      packageName + ".metrics.ApplicationLongSumObserver$AgentResultLongSumObserver",
      packageName + ".metrics.ApplicationLongSumObserver$ApplicationResultLongSumObserver",
      packageName + ".metrics.ApplicationLongUpDownCounter",
      packageName + ".metrics.ApplicationLongUpDownCounter$BoundInstrument",
      packageName + ".metrics.ApplicationLongUpDownCounter$Builder",
      packageName + ".metrics.ApplicationLongUpDownSumObserver",
      packageName + ".metrics.ApplicationLongUpDownSumObserver$Builder",
      packageName + ".metrics.ApplicationLongUpDownSumObserver$AgentResultLongUpDownSumObserver",
      packageName
          + ".metrics.ApplicationLongUpDownSumObserver$ApplicationResultLongUpDownSumObserver",
      packageName + ".metrics.ApplicationLongValueObserver",
      packageName + ".metrics.ApplicationLongValueObserver$Builder",
      packageName + ".metrics.ApplicationLongValueObserver$AgentResultLongValueObserver",
      packageName + ".metrics.ApplicationLongValueObserver$ApplicationResultLongValueObserver",
      packageName + ".metrics.ApplicationLongValueRecorder",
      packageName + ".metrics.ApplicationLongValueRecorder$BoundInstrument",
      packageName + ".metrics.ApplicationLongValueRecorder$Builder",
      packageName + ".metrics.ApplicationMeter",
      packageName + ".metrics.ApplicationMeterProvider",
      packageName + ".trace.Bridging",
      packageName + ".trace.Bridging$1",
      packageName + ".trace.Bridging$2",
      packageName + ".trace.TracingContextUtils",
      packageName + ".trace.ApplicationSpan",
      packageName + ".trace.ApplicationSpan$Builder",
      packageName + ".trace.ApplicationTracer",
      packageName + ".trace.ApplicationTracerProvider",
      packageName + ".LabelBridging",
      packageName + ".LabelBridging$Consumer"
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("application.io.grpc.Context", "io.grpc.Context");
  }
}
