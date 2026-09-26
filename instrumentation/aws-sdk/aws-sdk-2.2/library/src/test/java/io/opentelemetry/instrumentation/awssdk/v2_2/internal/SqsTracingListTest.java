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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    assertSplitTraversal();
  }

  @Test
  void rootListIteratorsTraceTraversal() {
    assertListIteratorTraversal();

    testing.waitForTraces(2);
    assertThat(testing.spans()).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("unusedTraversalSelections")
  void unusedTraversalDoesNotPreventLaterProcessing(Function<TracingList, ?> selectTraversal) {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());

    assertThat(selectTraversal.apply(tracingList)).isNotNull();
    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());

    testing.waitForTraces(1);
    assertThat(testing.spans()).hasSize(1);
  }

  private static Stream<Arguments> unusedTraversalSelections() {
    return Stream.of(
        argumentSet(
            "root iterator", (Function<TracingList, ?>) tracingList -> tracingList.iterator()),
        argumentSet(
            "root list iterator",
            (Function<TracingList, ?>) tracingList -> tracingList.listIterator()),
        argumentSet(
            "root indexed list iterator",
            (Function<TracingList, ?>) tracingList -> tracingList.listIterator(0)),
        argumentSet(
            "root spliterator",
            (Function<TracingList, ?>) tracingList -> tracingList.spliterator()));
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
  void rootAndSubListCrossApiTraversalsTraceOnlyRootPasses() {
    TracingList tracingList = tracingMessages(2, new ArrayList<>());
    List<Message> view = tracingList.subList(0, tracingList.size()).subList(0, 1);

    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    view.spliterator()
        .forEachRemaining(
            unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    tracingList
        .listIterator()
        .forEachRemaining(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    view.iterator()
        .forEachRemaining(
            unused -> assertThat(Span.current().getSpanContext().isValid()).isFalse());

    testing.waitForTraces(4);
    assertThat(testing.spans()).hasSize(4);
  }

  @ParameterizedTest
  @MethodSource("processingTraversals")
  void repeatedRootTraversalsTraceWhileSubListsDoNot(BiConsumer<List<Message>, Boolean> traverse) {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());
    List<Message> view = tracingList.subList(0, 1).subList(0, 1);

    traverse.accept(tracingList, true);
    traverse.accept(tracingList, true);
    traverse.accept(view, false);
    traverse.accept(view, false);

    testing.waitForTraces(2);
    assertThat(testing.spans()).hasSize(2);
  }

  private static Stream<Arguments> processingTraversals() {
    return Stream.of(
        argumentSet(
            "iterator",
            (BiConsumer<List<Message>, Boolean>)
                (messages, trace) ->
                    messages
                        .iterator()
                        .forEachRemaining(
                            unused ->
                                assertThat(Span.current().getSpanContext().isValid())
                                    .isEqualTo(trace))),
        argumentSet(
            "list iterator",
            (BiConsumer<List<Message>, Boolean>)
                (messages, trace) ->
                    messages
                        .listIterator()
                        .forEachRemaining(
                            unused ->
                                assertThat(Span.current().getSpanContext().isValid())
                                    .isEqualTo(trace))),
        argumentSet(
            "forEach",
            (BiConsumer<List<Message>, Boolean>)
                (messages, trace) ->
                    messages.forEach(
                        unused ->
                            assertThat(Span.current().getSpanContext().isValid())
                                .isEqualTo(trace))),
        argumentSet(
            "spliterator",
            (BiConsumer<List<Message>, Boolean>)
                (messages, trace) ->
                    messages
                        .spliterator()
                        .forEachRemaining(
                            unused ->
                                assertThat(Span.current().getSpanContext().isValid())
                                    .isEqualTo(trace))));
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
  void splitSpliteratorEndsProcessingWhenActionThrows() {
    TracingList tracingList = tracingMessages(2, new ArrayList<>());
    Spliterator<Message> split = requireNonNull(tracingList.spliterator().trySplit());
    IllegalStateException failure = new IllegalStateException("processing failed");

    assertThatThrownBy(
            () ->
                split.tryAdvance(
                    unused -> {
                      assertThat(Span.current().getSpanContext().isValid()).isTrue();
                      throw failure;
                    }))
        .isSameAs(failure);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();

    testing.waitForTraces(1);
    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR));
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
  void rootEqualityHashCodeAndToStringDoNotTraceTraversal() {
    TracingList tracingList = tracingMessages(1, new ArrayList<>());
    TracingList sameMessages = tracingMessages(1, new ArrayList<>());
    List<Message> ordinary = singletonList(tracingList.get(0));
    assertThat(tracingList.equals(sameMessages)).isTrue();
    assertThat(tracingList.equals(ordinary)).isTrue();
    assertThat(tracingList.hashCode()).isEqualTo(ordinary.hashCode());
    assertThat(tracingList.toString()).contains("message-0");
    assertThat(testing.spans()).isEmpty();

    tracingList.forEach(unused -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    testing.waitForTraces(1);
    assertThat(testing.spans()).hasSize(1);
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
    assertIteratorForEachRemainingFailure();
  }

  private static void assertSplitTraversal() throws Exception {
    TracingList tracingList = tracingMessages(MESSAGE_COUNT, new ArrayList<>());
    ContextKey<String> markerKey = ContextKey.named("spliterator-test-marker");
    Context expectedContext = Context.root().with(markerKey, "present");

    Spliterator<Message> remainder;
    Spliterator<Message> split;
    long originalSize;
    int originalCharacteristics;
    try (Scope ignored = expectedContext.makeCurrent()) {
      remainder = tracingList.spliterator();
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

  private static void assertListIteratorTraversal() {
    TracingList forwardTracingList = tracingMessages(1, new ArrayList<>());
    ListIterator<Message> forward = forwardTracingList.listIterator();
    assertThat(forward.next().messageId()).isEqualTo("message-0");
    assertThat(forward.hasNext()).isFalse();

    TracingList backwardTracingList = tracingMessages(1, new ArrayList<>());
    ListIterator<Message> backward = backwardTracingList.listIterator(backwardTracingList.size());
    assertThat(backward.previous().messageId()).isEqualTo("message-0");
    assertThat(backward.hasPrevious()).isFalse();
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
