/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class SqsProcessingSelectionTest {

  @Test
  void selectsByEventIdentity() {
    SQSEvent event = new SQSEvent();
    SQSEvent otherEvent = new SQSEvent();

    Context selectedContext = SqsProcessingSelection.select(Context.root(), event);

    assertThat(SqsProcessingSelection.isSelected(selectedContext, event)).isTrue();
    assertThat(SqsProcessingSelection.isSelected(selectedContext, otherEvent)).isFalse();
    assertThat(SqsProcessingSelection.select(selectedContext, event)).isSameAs(selectedContext);
  }

  @Test
  void selectionDoesNotEscapeInvocationContext() {
    SQSEvent event = new SQSEvent();

    Context firstInvocation = SqsProcessingSelection.select(Context.root(), event);

    assertThat(SqsProcessingSelection.isSelected(firstInvocation, event)).isTrue();
    assertThat(SqsProcessingSelection.isSelected(Context.root(), event)).isFalse();
  }
}
