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

  private static final VirtualField<ExtLogRecord, Context> CONTEXT =
      VirtualField.find(ExtLogRecord.class, Context.class);

  public static SpanContext getSpanContext(ExtLogRecord record) {
    Context context = CONTEXT.get(record);
    if (context == null) {
      return SpanContext.getInvalid();
    }
    return Span.fromContext(context).getSpanContext();
  }

  public static void setSpanContext(ExtLogRecord record, Context context) {
    CONTEXT.set(record, context);
  }

  private JbossLogManagerHelper() {}
}
