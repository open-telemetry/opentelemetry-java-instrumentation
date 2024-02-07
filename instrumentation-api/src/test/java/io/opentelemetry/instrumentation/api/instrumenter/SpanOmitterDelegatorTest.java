/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class SpanOmitterDelegatorTest {
  @Test
  void returnContextAsReceived() {
    Context context = mock(Context.class);

    SpanSuppressor suppressor = SpanOmitterDelegator.create(Collections.emptyList());

    assertThat(suppressor.storeInContext(context, SpanKind.CLIENT, Span.getInvalid()))
        .isEqualTo(context);
  }

  @Test
  void suppressSpanWhenAtLeastOneOmitterOmitsIt() {
    Context context = mock(Context.class);
    List<SpanOmitter> omitters = new ArrayList<>();
    SpanOmitter omitter1 = createOmitter(false);
    SpanOmitter omitter2 = createOmitter(true);
    SpanOmitter omitter3 = createOmitter(false);
    omitters.add(omitter1);
    omitters.add(omitter2);
    omitters.add(omitter3);

    SpanSuppressor suppressor = SpanOmitterDelegator.create(omitters);

    assertThat(suppressor.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
    verify(omitter1).shouldOmit(context);
    verify(omitter2).shouldOmit(context);
    verify(omitter3, never()).shouldOmit(context);
  }

  @Test
  void shouldNotSuppressWhenNoOmitterOmitsIt() {
    Context context = mock(Context.class);
    List<SpanOmitter> omitters = new ArrayList<>();
    SpanOmitter omitter1 = createOmitter(false);
    SpanOmitter omitter2 = createOmitter(false);
    SpanOmitter omitter3 = createOmitter(false);
    omitters.add(omitter1);
    omitters.add(omitter2);
    omitters.add(omitter3);

    SpanSuppressor suppressor = SpanOmitterDelegator.create(omitters);

    assertThat(suppressor.shouldSuppress(context, SpanKind.CLIENT)).isFalse();
    InOrder inOrder = Mockito.inOrder(omitter1, omitter2, omitter3);
    inOrder.verify(omitter1).shouldOmit(context);
    inOrder.verify(omitter2).shouldOmit(context);
    inOrder.verify(omitter3).shouldOmit(context);
  }

  private static SpanOmitter createOmitter(boolean shouldOmit) {
    SpanOmitter omitter = mock(SpanOmitter.class);
    doReturn(shouldOmit).when(omitter).shouldOmit(any());
    return omitter;
  }
}
