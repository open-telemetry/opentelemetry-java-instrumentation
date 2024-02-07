package io.opentelemetry.instrumentation.api.instrumenter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MultiSpanSuppressorTest {

  @Test
  void delegateToSuppressorsInOrder() {
    Context context = Context.current();
    SpanKind spanKind = SpanKind.CLIENT;
    Span span = Span.getInvalid();
    SpanSuppressor suppressor1 = getSpanSuppressor(context, false);
    SpanSuppressor suppressor2 = getSpanSuppressor(context, false);

    SpanSuppressor suppressor = MultiSpanSuppressor.create(suppressor1, suppressor2);

    suppressor.shouldSuppress(context, spanKind);
    suppressor.storeInContext(context, spanKind, span);

    InOrder inOrder = inOrder(suppressor1, suppressor2);
    inOrder.verify(suppressor1).shouldSuppress(context, spanKind);
    inOrder.verify(suppressor2).shouldSuppress(context, spanKind);
    inOrder.verify(suppressor1).storeInContext(context, spanKind, span);
    inOrder.verify(suppressor2).storeInContext(context, spanKind, span);
  }

  @Test
  void shouldSuppressTrueWhenAtLeastOneReturnsTrue() {
    Context context = Context.current();
    SpanKind spanKind = SpanKind.CLIENT;
    SpanSuppressor suppressor1 = getSpanSuppressor(context, false);
    SpanSuppressor suppressor2 = getSpanSuppressor(context, true);
    SpanSuppressor suppressor3 = getSpanSuppressor(context, false);

    SpanSuppressor suppressor = MultiSpanSuppressor.create(suppressor1, suppressor2, suppressor3);

    Assertions.assertThat(suppressor.shouldSuppress(context,spanKind)).isTrue();
    verify(suppressor1).shouldSuppress(context, spanKind);
    verify(suppressor2).shouldSuppress(context, spanKind);
    verify(suppressor3, never()).shouldSuppress(any(), any());
  }

  @Test
  void shouldSuppressFalseWhenNoneReturnsTrue() {
    Context context = Context.current();
    SpanKind spanKind = SpanKind.CLIENT;
    SpanSuppressor suppressor1 = getSpanSuppressor(context, false);
    SpanSuppressor suppressor2 = getSpanSuppressor(context, false);
    SpanSuppressor suppressor3 = getSpanSuppressor(context, false);

    SpanSuppressor suppressor = MultiSpanSuppressor.create(suppressor1, suppressor2, suppressor3);

    Assertions.assertThat(suppressor.shouldSuppress(context,spanKind)).isFalse();
    verify(suppressor1).shouldSuppress(context, spanKind);
    verify(suppressor2).shouldSuppress(context, spanKind);
    verify(suppressor3).shouldSuppress(context, spanKind);
  }

  @Test
  void returnCompositeContext(){
    SpanKind spanKind = SpanKind.CLIENT;
    Span span = Span.getInvalid();
    Context original = mock(Context.class);
    Context fromSuppressor1 = mock(Context.class);
    Context fromSuppressor2 = mock(Context.class);
    SpanSuppressor suppressor1 = getSpanSuppressor(fromSuppressor1, false);
    SpanSuppressor suppressor2 = getSpanSuppressor(fromSuppressor2, false);

    SpanSuppressor suppressor = MultiSpanSuppressor.create(suppressor1, suppressor2);

    Assertions.assertThat(suppressor.storeInContext(original, spanKind, span)).isEqualTo(fromSuppressor2);
    verify(suppressor1).storeInContext(original, spanKind, span);
    verify(suppressor2).storeInContext(fromSuppressor1, spanKind, span);
  }

  private static SpanSuppressor getSpanSuppressor(Context contextToReturn, boolean shouldSuppress) {
    SpanSuppressor suppressor = mock(SpanSuppressor.class);
    doReturn(contextToReturn).when(suppressor).storeInContext(any(), any(), any());
    doReturn(shouldSuppress).when(suppressor).shouldSuppress(any(), any());

    return suppressor;
  }
}
