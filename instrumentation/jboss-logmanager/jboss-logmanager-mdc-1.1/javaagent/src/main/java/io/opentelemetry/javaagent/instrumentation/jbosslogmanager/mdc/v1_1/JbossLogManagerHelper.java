/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.mdc.v1_1;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.jboss.logmanager.ExtLogRecord;

public class JbossLogManagerHelper {

  public static SpanContext getSpanContext(ExtLogRecord record) {
    Context context = VirtualField.find(ExtLogRecord.class, Context.class).get(record);
    if (context == null) {
      return SpanContext.getInvalid();
    }
    return Span.fromContext(context).getSpanContext();
  }

  private JbossLogManagerHelper() {}
}
