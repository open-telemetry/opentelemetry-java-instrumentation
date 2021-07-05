/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

/** Types of supported propagators. */
public enum PropagationType {

  /** B3 Single */
  b3,

  /** B3 Multi */
  b3multi,

  /** W3C Trace Context */
  tracecontext,

  /** W3C Baggage */
  baggage,

  /** Jaeger */
  jaeger,

  /** AWS X-Ray */
   xray,

  /** OT Trace */
  ottrace,

}
