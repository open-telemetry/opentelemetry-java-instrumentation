/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.List;
import javax.annotation.Nullable;

final class SqsEventAttributesGetter extends SqsAttributesGetter<SQSEvent> {

  @Nullable
  @Override
  public String getDestination(SQSEvent event) {
    return SqsEventRecordAttributes.create(event).getCommonDestination();
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(SQSEvent event, @Nullable Void unused) {
    List<SQSMessage> records = event.getRecords();
    return records == null ? null : (long) records.size();
  }
}
