/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.sqs.model.Message;

class SqsTracingListTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-sdk-2.2";
  private static final int MESSAGE_COUNT = 8;
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
  void spliteratorCreatedBeforeProcessingHandoffDoesNotStartSdkProcessing() {
    Message message = Message.builder().messageId("message-id").build();
    @SuppressWarnings("unchecked")
    Instrumenter<SqsProcessRequest, Response> instrumenter = mock(Instrumenter.class);
    TracingList tracingList =
        TracingList.wrap(
            singletonList(message),
            singletonList(SqsMessageImpl.wrap(message)),
            instrumenter,
            new ExecutionAttributes(),
            new Response(SdkHttpResponse.builder().statusCode(200).build()),
            mock(TracingExecutionInterceptor.class),
            Context.root());

    Spliterator<Message> spliterator = tracingList.spliterator();
    tracingList.markProcessingOwnedOutsideSqsSdk();

    assertThat(spliterator.tryAdvance(value -> assertThat(value).isSameAs(message))).isTrue();
    assertThat(spliterator.tryAdvance(unused -> {})).isFalse();
    verifyNoInteractions(instrumenter);
  }

  @Test
  void rootSpliteratorTracesSplitCallbacks() throws Exception {
    assertSplitTraversal(false);
  }

  @Test
  void viewSpliteratorTracesSplitCallbacks() throws Exception {
    assertSplitTraversal(true);
  }

  @Test
  void rootAndViewListIteratorsTraceTraversal() {
    assertListIteratorTraversal(false);
    assertListIteratorTraversal(true);

    testing.waitForTraces(4);
    assertThat(testing.spans()).hasSize(4);
  }

  @Test
  void emptyRootAndViewRejectNullForEachAction() {
    assertThatThrownBy(() -> tracingMessages(0, new ArrayList<>()).forEach(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> tracingMessages(0, new ArrayList<>()).subList(0, 0).forEach(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> tracingMessages(0, new ArrayList<>()).iterator().forEachRemaining(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                tracingMessages(0, new ArrayList<>())
                    .subList(0, 0)
                    .iterator()
                    .forEachRemaining(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rootForEachEndsProcessingWhenActionThrows() {
    assertForEachFailure(false);
  }

  @Test
  void viewForEachEndsProcessingWhenActionThrows() {
    assertForEachFailure(true);
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
  void subListPreservesRandomAccess() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());

    assertThat(tracingList.subList(0, 1)).isInstanceOf(RandomAccess.class);
    assertThat(tracingList.subList(0, 1).subList(0, 1)).isInstanceOf(RandomAccess.class);
  }

  @Test
  void viewLookupMethodsDoNotTraceTraversal() {
    List<List<Message>> views = new ArrayList<>();
    ContextKey<String> markerKey = ContextKey.named("lookup-test-marker");
    Context expectedContext = Context.root().with(markerKey, "present");

    try (Scope ignored = expectedContext.makeCurrent()) {
      TracingList containsList = tracingMessages(1, new ArrayList<>());
      List<Message> containsView = containsList.subList(0, containsList.size());
      views.add(containsView);
      assertThat(containsView.contains(containsView.get(0))).isTrue();

      TracingList indexOfList = tracingMessages(1, new ArrayList<>());
      List<Message> indexOfView = indexOfList.subList(0, indexOfList.size());
      views.add(indexOfView);
      assertThat(indexOfView.indexOf(indexOfView.get(0))).isZero();

      TracingList lastIndexOfList = tracingMessages(1, new ArrayList<>());
      List<Message> lastIndexOfView = lastIndexOfList.subList(0, lastIndexOfList.size());
      views.add(lastIndexOfView);
      assertThat(lastIndexOfView.lastIndexOf(lastIndexOfView.get(0))).isZero();

      assertThat(Context.current()).isSameAs(expectedContext);
      assertThat(testing.spans()).isEmpty();
    }

    views.forEach(
        view -> {
          Iterator<Message> iterator = view.iterator();
          assertThat(iterator.next()).isSameAs(view.get(0));
          assertThat(iterator.hasNext()).isFalse();
        });

    testing.waitForTraces(3);
    assertThat(testing.spans()).hasSize(3);
  }

  @Test
  void rootAndViewEqualityAndHashCodeDoNotTraceTraversal() {
    TracingList tracingList = tracingMessages(2, new ArrayList<>());
    List<Message> first = tracingList.subList(0, 1);
    List<Message> second = tracingList.subList(1, 2);
    List<TracingList> comparedLists = new ArrayList<>();
    ContextKey<String> markerKey = ContextKey.named("equality-test-marker");
    Context expectedContext = Context.root().with(markerKey, "present");

    try (Scope ignored = expectedContext.makeCurrent()) {
      assertThat(first).isNotEqualTo(second);
      assertThat(first.hashCode()).isNotZero();
      comparedLists.add(assertRootAndViewEqualityDoesNotTrace(true, true));
      comparedLists.add(assertRootAndViewEqualityDoesNotTrace(true, false));
      comparedLists.add(assertRootAndViewEqualityDoesNotTrace(false, true));
      comparedLists.add(assertRootAndViewEqualityDoesNotTrace(false, false));

      assertThat(Context.current()).isSameAs(expectedContext);
      assertThat(testing.spans()).isEmpty();
    }

    Iterator<Message> iterator = first.iterator();
    assertThat(iterator.next()).isSameAs(first.get(0));
    assertThat(iterator.hasNext()).isFalse();
    comparedLists.forEach(
        list -> {
          Iterator<Message> comparedIterator = list.iterator();
          assertThat(comparedIterator.next()).isSameAs(list.get(0));
          assertThat(comparedIterator.hasNext()).isFalse();
        });

    testing.waitForTraces(5);
    assertThat(testing.spans()).hasSize(5);
  }

  @Test
  void explicitSuppressionSkipsCallbackProcessing() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());
    AtomicBoolean callbackInvoked = new AtomicBoolean();

    InstrumentationUtil.suppressInstrumentation(
        () ->
            tracingList
                .spliterator()
                .forEachRemaining(
                    unused -> {
                      callbackInvoked.set(true);
                      assertThat(Span.current().getSpanContext().isValid()).isFalse();
                    }));

    assertThat(callbackInvoked).isTrue();
    assertThat(testing.spans()).isEmpty();
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
      tracingList
          .spliterator()
          .forEachRemaining(unused -> callbackSpanContext.set(Span.current().getSpanContext()));
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
  void rootIteratorForEachRemainingEndsProcessingWhenActionThrows() {
    assertIteratorForEachRemainingFailure(false);
  }

  @Test
  void viewIteratorForEachRemainingEndsProcessingWhenActionThrows() {
    assertIteratorForEachRemainingFailure(true);
  }

  private static void assertSplitTraversal(boolean useView) throws Exception {
    TracingList tracingList = tracingMessages(MESSAGE_COUNT, new ArrayList<>());
    List<Message> messages = useView ? tracingList.subList(0, tracingList.size()) : tracingList;
    ContextKey<String> markerKey = ContextKey.named("spliterator-test-marker");
    Context expectedContext = Context.root().with(markerKey, "present");

    Spliterator<Message> remainder;
    Spliterator<Message> split;
    long originalSize;
    int originalCharacteristics;
    try (Scope ignored = expectedContext.makeCurrent()) {
      remainder = messages.spliterator();
      originalSize = remainder.estimateSize();
      originalCharacteristics = remainder.characteristics();
      split = requireNonNull(remainder.trySplit());

      assertThat(Context.current()).isSameAs(expectedContext);
      assertThat(testing.spans()).isEmpty();
    }

    assertThat(split.estimateSize() + remainder.estimateSize()).isEqualTo(originalSize);
    assertThat(split.characteristics()).isEqualTo(originalCharacteristics);
    assertThat(remainder.characteristics()).isEqualTo(originalCharacteristics);

    Map<String, String> callbackSpanIds = new ConcurrentHashMap<>();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first = executor.submit(() -> process(split, callbackSpanIds));
      Future<?> second = executor.submit(() -> process(remainder, callbackSpanIds));
      first.get(10, SECONDS);
      second.get(10, SECONDS);
    } finally {
      executor.shutdownNow();
    }

    testing.waitForTraces(MESSAGE_COUNT);
    assertThat(callbackSpanIds).hasSize(MESSAGE_COUNT);
    assertThat(testing.spans())
        .hasSize(MESSAGE_COUNT)
        .allSatisfy(
            span -> {
              String messageId = requireNonNull(span.getAttributes().get(MESSAGING_MESSAGE_ID));
              assertThat(callbackSpanIds).containsEntry(messageId, span.getSpanId());
            });

    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME,
          "messaging.client.consumed.messages",
          metrics ->
              metrics
                  .singleElement()
                  .satisfies(
                      metric ->
                          assertThat(metric.getLongSumData().getPoints())
                              .singleElement()
                              .satisfies(
                                  point -> assertThat(point.getValue()).isEqualTo(MESSAGE_COUNT))));
    }
  }

  private static void assertListIteratorTraversal(boolean useView) {
    TracingList forwardTracingList = tracingMessages(1, new ArrayList<>());
    List<Message> forwardMessages =
        useView ? forwardTracingList.subList(0, forwardTracingList.size()) : forwardTracingList;
    ListIterator<Message> forward = forwardMessages.listIterator();
    assertThat(forward.next().messageId()).isEqualTo("message-0");
    assertThat(forward.hasNext()).isFalse();

    TracingList backwardTracingList = tracingMessages(1, new ArrayList<>());
    List<Message> backwardMessages =
        useView ? backwardTracingList.subList(0, backwardTracingList.size()) : backwardTracingList;
    ListIterator<Message> backward = backwardMessages.listIterator(backwardMessages.size());
    assertThat(backward.previous().messageId()).isEqualTo("message-0");
    assertThat(backward.hasPrevious()).isFalse();
  }

  private static TracingList assertRootAndViewEqualityDoesNotTrace(
      boolean viewFirst, boolean equal) {
    TracingList first = tracingMessages(1, new ArrayList<>());
    TracingList second = tracingMessages(1, new ArrayList<>());
    if (!equal) {
      first.set(0, Message.builder().messageId("different-message").build());
    }

    List<Message> left = viewFirst ? first.subList(0, first.size()) : first;
    List<Message> right = viewFirst ? second : second.subList(0, second.size());
    assertThat(left.equals(right)).isEqualTo(equal);
    return second;
  }

  private static void assertIteratorForEachRemainingFailure(boolean useView) {
    TracingList tracingList = tracingMessages(2, new ArrayList<>());
    List<Message> messages = useView ? tracingList.subList(0, tracingList.size()) : tracingList;
    IllegalStateException failure = new IllegalStateException("processing failed");
    ContextKey<String> markerKey = ContextKey.named("iterator-test-marker");
    Context expectedContext = Context.root().with(markerKey, "present");
    SpanContext firstSpanContext;
    AtomicReference<SpanContext> callbackSpanContext = new AtomicReference<>();

    try (Scope ignored = expectedContext.makeCurrent()) {
      Iterator<Message> iterator = messages.iterator();
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

  private static void assertForEachFailure(boolean useView) {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());
    List<Message> messages = useView ? tracingList.subList(0, tracingList.size()) : tracingList;
    IllegalStateException failure = new IllegalStateException("processing failed");
    ContextKey<String> markerKey = ContextKey.named("for-each-test-marker");
    Context expectedContext = Context.root().with(markerKey, "present");

    try (Scope ignored = expectedContext.makeCurrent()) {
      assertThatThrownBy(
              () ->
                  messages.forEach(
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

  private static void process(Spliterator<Message> messages, Map<String, String> callbackSpanIds) {
    Context previous = Context.current();
    messages.forEachRemaining(
        message -> {
          assertThat(Span.current().getSpanContext().isValid()).isTrue();
          assertThat(
                  callbackSpanIds.put(
                      message.messageId(), Span.current().getSpanContext().getSpanId()))
              .isNull();
        });
    assertThat(Context.current()).isSameAs(previous);
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
