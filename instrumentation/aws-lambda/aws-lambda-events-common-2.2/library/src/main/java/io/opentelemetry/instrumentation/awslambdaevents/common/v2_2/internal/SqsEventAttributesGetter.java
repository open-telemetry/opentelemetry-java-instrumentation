/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.List;
import javax.annotation.Nullable;

final class SqsEventAttributesGetter extends SqsAttributesGetter<SQSEvent> {

  @Nullable
  @Override
  public String getDestination(SQSEvent event) {
    return emitStableMessagingSemconv()
        ? SqsEventRecordAttributes.create(event).getCommonDestination()
        : null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(SQSEvent event, @Nullable Void unused) {
    if (!emitStableMessagingSemconv()) {
      return null;
    }
    List<SQSMessage> records = event.getRecords();
    return records == null ? null : (long) records.size();
  }

  static String source(SQSEvent event) {
    String source = "multiple_sources";
    List<SQSMessage> records = event.getRecords();
    if (records != null && !records.isEmpty()) {
      String messageSource = records.get(0).getEventSource();
      for (int i = 1; i < records.size(); i++) {
        SQSMessage message = records.get(i);
        if (!message.getEventSource().equals(messageSource)) {
          messageSource = null;
          break;
        }
      }
      if (messageSource != null) {
        source = messageSource;
      }
    }
    return source;
  }
}
