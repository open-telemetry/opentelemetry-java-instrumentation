/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opencensusshim;

import io.opentelemetry.api.trace.SpanContext;

/*
Needed to expose the package-private SpanConverter's `mapSpanContext()` method.
 */
public class SpanConverterProxy {

  private SpanConverterProxy() {}

  public static SpanContext mapSpanContext(io.opencensus.trace.SpanContext ctx) {
    return SpanConverter.mapSpanContext(ctx);
  }
}
