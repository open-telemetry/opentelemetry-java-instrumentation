/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.sqs.model.Message;

class SqsTracingListTest {

  private static final AttributeKey<String> MESSAGING_MESSAGE_ID =
      AttributeKey.stringKey("messaging.message.id");

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  @SuppressWarnings("unchecked")
  void iteratorCreatedBeforeProcessingHandoffDoesNotStartSdkProcessing() {
    Message message = Message.builder().messageId("message-id").build();
    Instrumenter<SqsProcessRequest, Response> instrumenter = mock(Instrumenter.class);
    when(instrumenter.start(any(), any())).thenReturn(Context.root());
    TracingList tracingList =
        TracingList.wrap(
            singletonList(message),
            singletonList(SqsMessageImpl.wrap(message)),
            instrumenter,
            new ExecutionAttributes(),
            new Response(SdkHttpResponse.builder().statusCode(200).build()),
            mock(TracingExecutionInterceptor.class),
            Context.root());

    Iterator<Message> iterator = tracingList.iterator();
    tracingList.markProcessingOwnedOutsideSqsSdk();

    assertThat(iterator.next()).isSameAs(message);
    assertThat(iterator.hasNext()).isFalse();
    verifyNoInteractions(instrumenter);
  }

  @Test
  void unusedFirstIteratorConsumesProcessingChance() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());

    assertThat(tracingList.iterator()).isNotNull();
    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void otherTraversalApisRemainUntracedWithoutClaimingFirstIterator() {
    TracingList tracingList = tracingMessages(2, new ArrayList<>());

    tracingList
        .listIterator()
        .forEachRemaining(
            unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    assertThat(tracingList.listIterator(tracingList.size()).previous().messageId())
        .isEqualTo("message-1");
    Spliterator<Message> spliterator = tracingList.spliterator();
    requireNonNull(spliterator.trySplit())
        .forEachRemaining(
            unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    spliterator.forEachRemaining(
        unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    assertThat(
            tracingList.stream()
                .mapToInt(
                    unused -> {
                      assertThat(Span.current().getSpanContext().isValid()).isFalse();
                      return 1;
                    })
                .sum())
        .isEqualTo(2);
    assertThat(testing.spans()).isEmpty();

    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    testing.waitForTraces(2);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void firstRootIteratorTracesOnlyOnce() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());

    tracingList
        .iterator()
        .forEachRemaining(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    tracingList
        .iterator()
        .forEachRemaining(
            unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());

    testing.waitForTraces(1);
    assertThat(testing.spans()).hasSize(1);
  }

  @Test
  void emptySubListForEachDoesNotPreventRootProcessing() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());

    tracingList.subList(0, 0).forEach(unused -> {});
    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());

    testing.waitForTraces(1);
    assertThat(testing.spans()).hasSize(1);
  }

  @Test
  void rootAndSubListCrossApiTraversalsTraceOnlyFirstRootIterator() {
    TracingList tracingList = tracingMessages(2, new ArrayList<>());
    List<Message> view = tracingList.subList(0, tracingList.size()).subList(0, 1);

    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    view.spliterator()
        .forEachRemaining(
            unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    tracingList
        .listIterator()
        .forEachRemaining(
            unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    view.iterator()
        .forEachRemaining(
            unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    tracingList
        .iterator()
        .forEachRemaining(
            unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());

    testing.waitForTraces(2);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void processingHandoffStopsLaterTraversals() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());

    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    tracingList.markProcessingOwnedOutsideSqsSdk();
    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    tracingList
        .spliterator()
        .forEachRemaining(
            unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());

    testing.waitForTraces(1);
    assertThat(testing.spans()).hasSize(1);
  }

  @Test
  void splitSpliteratorDoesNotStartProcessingWhenActionThrows() {
    TracingList tracingList = tracingMessages(2, new ArrayList<>());
    Spliterator<Message> split = requireNonNull(tracingList.spliterator().trySplit());
    IllegalStateException failure = new IllegalStateException("processing failed");

    assertThatThrownBy(
            () ->
                split.tryAdvance(
                    unused -> {
                      assertThat(Span.current().getSpanContext().isValid()).isFalse();
                      throw failure;
                    }))
        .isSameAs(failure);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void emptyRootRejectsNullForEachAction() {
    assertThatThrownBy(() -> tracingMessages(0, new ArrayList<>()).forEach(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> tracingMessages(0, new ArrayList<>()).iterator().forEachRemaining(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rootForEachEndsProcessingWhenActionThrows() {
    assertForEachFailure();
  }

  @Test
  void forEachEndsProcessingWhenActionSneakyThrowsCheckedException() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());
    IOException failure = new IOException("processing failed");

    assertThatThrownBy(() -> tracingList.forEach(unused -> throwUnchecked(failure)))
        .isSameAs(failure);

    testing.waitForTraces(1);
    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(MESSAGING_MESSAGE_ID)).isEqualTo("message-0");
              assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            });
  }

  @Test
  void subListsKeepArrayListEqualityAndMutationWithoutTracing() {
    TracingList tracingList = tracingMessages(3, new ArrayList<>());
    List<Message> ordinary = new ArrayList<>(tracingList);
    List<Message> view = tracingList.subList(0, 2);
    List<Message> nested = view.subList(0, 1);
    List<Message> ordinaryView = ordinary.subList(0, 2);
    List<Message> ordinaryNested = ordinaryView.subList(0, 1);

    assertThat(view).isInstanceOf(RandomAccess.class).isEqualTo(ordinaryView);
    assertThat(nested).isInstanceOf(RandomAccess.class).isEqualTo(ordinaryNested);
    assertThat(view.hashCode()).isEqualTo(ordinaryView.hashCode());
    assertThat(nested.hashCode()).isEqualTo(ordinaryNested.hashCode());

    Message replacement = Message.builder().messageId("replacement").build();
    assertThat(nested.set(0, replacement)).isSameAs(ordinaryNested.set(0, replacement));
    assertThat(nested).isEqualTo(ordinaryNested);
    nested.clear();
    ordinaryNested.clear();
    assertThat(view).isEqualTo(ordinaryView);
    assertThat(tracingList).hasSameSizeAs(ordinary);
    assertThat(tracingList.get(0)).isSameAs(ordinary.get(0));
    assertThat(testing.spans()).isEmpty();

    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    testing.waitForTraces(2);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void firstIteratorTracesMessagesAddedOrReplacedBeforeTraversal() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());
    Message replacement = Message.builder().messageId("replacement").build();
    Message added = Message.builder().messageId("added").build();
    tracingList.set(0, replacement);
    tracingList.add(added);

    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());

    testing.waitForTraces(2);
    assertThat(testing.spans())
        .extracting(span -> span.getAttributes().get(MESSAGING_MESSAGE_ID))
        .containsExactlyInAnyOrder("replacement", "added");
  }

  @Test
  void firstIteratorNextTracesReplacedMessage() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());
    tracingList.set(0, Message.builder().messageId("replacement").build());

    Iterator<Message> iterator = tracingList.iterator();
    assertThat(iterator.next().messageId()).isEqualTo("replacement");
    assertThat(Span.current().getSpanContext().isValid()).isTrue();
    assertThat(iterator.hasNext()).isFalse();

    testing.waitForTraces(1);
    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span ->
                assertThat(span.getAttributes().get(MESSAGING_MESSAGE_ID))
                    .isEqualTo("replacement"));
  }

  @Test
  void callbackProcessingIgnoresUnrelatedCurrentConsumerAndUsesCapturedParent() {
    Instrumenter<SqsProcessRequest, Response> instrumenter = newConsumerProcessInstrumenter();
    ExecutionAttributes request = new ExecutionAttributes();
    SqsMessage unrelatedMessage =
        SqsMessageImpl.wrap(Message.builder().messageId("unrelated-message").build());
    SqsProcessRequest unrelatedRequest = SqsProcessRequest.create(request, unrelatedMessage);
    assertThat(instrumenter.shouldStart(Context.root(), unrelatedRequest)).isTrue();
    Context unrelatedContext = instrumenter.start(Context.root(), unrelatedRequest);
    Span capturedParent =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("captured-parent").startSpan();
    TracingList tracingList =
        tracingMessages(1, new ArrayList<>(), instrumenter, Context.root().with(capturedParent));
    SpanContext unrelatedSpanContext = Span.fromContext(unrelatedContext).getSpanContext();
    AtomicReference<SpanContext> callbackSpanContext = new AtomicReference<>();

    try (Scope ignored = unrelatedContext.makeCurrent()) {
      tracingList.forEach(unused -> callbackSpanContext.set(Span.current().getSpanContext()));
      assertThat(Span.current().getSpanContext()).isEqualTo(unrelatedSpanContext);
    } finally {
      instrumenter.end(unrelatedContext, unrelatedRequest, null, null);
      capturedParent.end();
    }

    testing.waitForTraces(2);
    SpanContext callbackContext = requireNonNull(callbackSpanContext.get());
    assertThat(testing.spans())
        .filteredOn(span -> "message-0".equals(span.getAttributes().get(MESSAGING_MESSAGE_ID)))
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getSpanId()).isEqualTo(callbackContext.getSpanId());
              assertThat(span.getTraceId()).isEqualTo(capturedParent.getSpanContext().getTraceId());
              assertThat(span.getParentSpanId())
                  .isEqualTo(capturedParent.getSpanContext().getSpanId());
            });
  }

  @Test
  void capturedProcessContextSuppressesNestedTraversals() {
    Instrumenter<SqsProcessRequest, Response> instrumenter = newConsumerProcessInstrumenter();
    SqsProcessRequest outerRequest =
        SqsProcessRequest.create(
            new ExecutionAttributes(),
            SqsMessageImpl.wrap(Message.builder().messageId("outer-message").build()));
    Context parentContext = instrumenter.start(Context.root(), outerRequest);
    assertThat(SpanKey.CONSUMER_PROCESS.fromContextOrNull(parentContext)).isNotNull();
    TracingList tracingList = tracingMessages(1, new ArrayList<>(), instrumenter, parentContext);
    TracingList callbackList = tracingMessages(1, new ArrayList<>(), instrumenter, parentContext);

    try {
      Iterator<Message> iterator = tracingList.iterator();
      assertThat(iterator.next().messageId()).isEqualTo("message-0");
      assertThat(Span.current().getSpanContext().isValid()).isFalse();
      assertThat(iterator.hasNext()).isFalse();

      callbackList.forEach(
          unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
      tracingList
          .spliterator()
          .forEachRemaining(
              unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
      assertThat(tracingList.listIterator().next().messageId()).isEqualTo("message-0");
      assertThat(Span.current().getSpanContext().isValid()).isFalse();
    } finally {
      instrumenter.end(parentContext, outerRequest, null, null);
    }

    testing.waitForTraces(1);
    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span ->
                assertThat(span.getAttributes().get(MESSAGING_MESSAGE_ID))
                    .isEqualTo("outer-message"));
  }

  @Test
  void abandonedIteratorDoesNotSuppressUnrelatedDelivery() {
    TracingList first = tracingMessages(1, new ArrayList<>());
    TracingList unrelated = tracingMessages(1, new ArrayList<>());
    Iterator<Message> iterator = first.iterator();

    try {
      assertThat(iterator.next().messageId()).isEqualTo("message-0");
      SpanContext firstSpan = Span.current().getSpanContext();
      assertThat(firstSpan.isValid()).isTrue();

      unrelated.forEach(
          unused -> {
            assertThat(Span.current().getSpanContext().isValid()).isTrue();
            assertThat(Span.current().getSpanContext()).isNotEqualTo(firstSpan);
          });
      assertThat(Span.current().getSpanContext()).isEqualTo(firstSpan);
    } finally {
      assertThat(iterator.hasNext()).isFalse();
    }

    testing.waitForTraces(2);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void rootIteratorForEachRemainingEndsProcessingWhenActionThrows() {
    assertIteratorForEachRemainingFailure();
  }

  private static void assertIteratorForEachRemainingFailure() {
    TracingList tracingList = tracingMessages(2, new ArrayList<>());
    IllegalStateException failure = new IllegalStateException("processing failed");
    ContextKey<String> markerKey = ContextKey.named("iterator-test-marker");
    Context expectedContext = Context.root().with(markerKey, "present");
    SpanContext firstSpanContext;
    AtomicReference<SpanContext> callbackSpanContext = new AtomicReference<>();

    try (Scope ignored = expectedContext.makeCurrent()) {
      Iterator<Message> iterator = tracingList.iterator();
      assertThat(iterator.next().messageId()).isEqualTo("message-0");
      firstSpanContext = Span.current().getSpanContext();
      assertThat(firstSpanContext.isValid()).isTrue();

      assertThatThrownBy(
              () ->
                  iterator.forEachRemaining(
                      message -> {
                        assertThat(message.messageId()).isEqualTo("message-1");
                        callbackSpanContext.set(Span.current().getSpanContext());
                        throw failure;
                      }))
          .isSameAs(failure);
      assertThat(Context.current()).isSameAs(expectedContext);
    }
    testing.waitForTraces(2);
    SpanContext callbackContext = requireNonNull(callbackSpanContext.get());
    assertThat(testing.spans())
        .anySatisfy(
            span -> {
              assertThat(span.getAttributes().get(MESSAGING_MESSAGE_ID)).isEqualTo("message-0");
              assertThat(span.getSpanId()).isEqualTo(firstSpanContext.getSpanId());
              assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
            })
        .anySatisfy(
            span -> {
              assertThat(span.getAttributes().get(MESSAGING_MESSAGE_ID)).isEqualTo("message-1");
              assertThat(span.getSpanId()).isEqualTo(callbackContext.getSpanId());
              assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            });
  }

  private static void assertForEachFailure() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());
    IllegalStateException failure = new IllegalStateException("processing failed");
    ContextKey<String> markerKey = ContextKey.named("for-each-test-marker");
    Context expectedContext = Context.root().with(markerKey, "present");

    try (Scope ignored = expectedContext.makeCurrent()) {
      assertThatThrownBy(
              () ->
                  tracingList.forEach(
                      unused -> {
                        throw failure;
                      }))
          .isSameAs(failure);
      assertThat(Context.current()).isSameAs(expectedContext);
    }

    testing.waitForTraces(1);
    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(MESSAGING_MESSAGE_ID)).isEqualTo("message-0");
              assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            });
  }

  private static TracingList tracingMessages(int messageCount, List<SqsMessage> tracingMessages) {
    return tracingMessages(
        messageCount, tracingMessages, newConsumerProcessInstrumenter(), Context.root());
  }

  private static TracingList tracingMessages(
      int messageCount,
      List<SqsMessage> tracingMessages,
      Instrumenter<SqsProcessRequest, Response> instrumenter,
      Context processParentContext) {
    List<Message> messages = new ArrayList<>(messageCount);
    for (int i = 0; i < messageCount; i++) {
      Message message = Message.builder().messageId("message-" + i).build();
      messages.add(message);
      tracingMessages.add(SqsMessageImpl.wrap(message));
    }
    return TracingList.wrap(
        messages,
        tracingMessages,
        instrumenter,
        new ExecutionAttributes(),
        new Response(SdkHttpResponse.builder().statusCode(200).build()),
        mock(TracingExecutionInterceptor.class),
        processParentContext);
  }

  private static Instrumenter<SqsProcessRequest, Response> newConsumerProcessInstrumenter() {
    return new AwsSdkInstrumenterFactory(
            testing.getOpenTelemetry(), null, IncludeExclude.builder().build(), false, false, false)
        .consumerProcessInstrumenter();
  }

  // The unchecked cast simulates a callback that uses a sneaky throw.
  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  private static <T extends Throwable> void throwUnchecked(Throwable t) throws T {
    throw (T) t;
  }
}
