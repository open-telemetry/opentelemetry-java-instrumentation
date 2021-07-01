/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

/** Types of supported propagators. */
public enum PropagationType {

  /** B3Propagator */
  B3,

  /** W3CTraceContextPropagator */
  W3C,

  /** W3CBaggagePropagator */
  BAGGAGE,

  /** JaegerPropagator */
  JAEGER,

  /** OtTracePropagator */
  OT_TRACER,

  /** NoOp propagator */
  NOOP
}
