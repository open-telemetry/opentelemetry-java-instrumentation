/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import io.opentelemetry.javaagent.tooling.Instrumenter;

public abstract class AbstractInstrumentation extends Instrumenter.Default {
  public AbstractInstrumentation() {
    super("opentelemetry-api");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".context.AgentContextStorage",
      packageName + ".context.AgentContextStorage$AgentContextWrapper",
      packageName + ".context.propagation.ApplicationContextPropagators",
      packageName + ".context.propagation.ApplicationTextMapPropagator",
      packageName + ".context.propagation.ApplicationTextMapPropagator$AgentSetter",
      packageName + ".context.propagation.ApplicationTextMapPropagator$AgentGetter",
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
}
