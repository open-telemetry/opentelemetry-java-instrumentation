/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import io.opentelemetry.api.trace.SpanKind;

public class S3SpanDecorator extends BaseSpanDecorator {

  @Override
  public SpanKind getInitiatorSpanKind() {
    return SpanKind.INTERNAL;
  }

  @Override
  public SpanKind getReceiverSpanKind() {
    return SpanKind.INTERNAL;
  }
}
