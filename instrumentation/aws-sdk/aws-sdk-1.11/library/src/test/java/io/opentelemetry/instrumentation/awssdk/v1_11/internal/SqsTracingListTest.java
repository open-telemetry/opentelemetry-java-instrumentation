/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.amazonaws.DefaultRequest;
import com.amazonaws.Response;
import com.amazonaws.internal.SdkInternalList;
import com.amazonaws.services.sqs.model.Message;
import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SqsTracingListTest {

  private static final ContextKey<String> APPLICATION_KEY = ContextKey.named("application-key");

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @MethodSource("traversals")
  void observableTraversalFinishesProcessSpans(Consumer<List<Message>> traversal) {
    Context previous = Context.current();
    traversal.accept(tracingMessages());

    assertThat(Context.current()).isSameAs(previous);
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("process").hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("process").hasNoParent()));
  }

  private static Stream<Arguments> traversals() {
    return Stream.of(
        argumentSet(
            "iterator",
            (Consumer<List<Message>>)
                messages -> {
                  for (Message ignored : messages) {
                    assertThat(Span.current().getSpanContext().isValid()).isTrue();
                  }
                }),
        argumentSet(
            "forEach",
            (Consumer<List<Message>>) messages -> messages.forEach(SqsTracingListTest::processing)),
        argumentSet(
            "forEachRemaining",
            (Consumer<List<Message>>)
                messages -> messages.iterator().forEachRemaining(SqsTracingListTest::processing)),
        argumentSet(
            "view",
            (Consumer<List<Message>>)
                messages -> messages.subList(0, 2).forEach(SqsTracingListTest::processing)),
        argumentSet(
            "nested view",
            (Consumer<List<Message>>)
                messages ->
                    messages.subList(0, 2).subList(0, 2).forEach(SqsTracingListTest::processing)),
        argumentSet(
            "spliterator",
            (Consumer<List<Message>>)
                messages ->
                    messages.spliterator().forEachRemaining(SqsTracingListTest::processing)),
        argumentSet(
            "tryAdvance",
            (Consumer<List<Message>>)
                messages -> {
                  Spliterator<Message> spliterator = messages.spliterator();
                  while (spliterator.tryAdvance(SqsTracingListTest::processing)) {
                    assertThat(Span.current().getSpanContext().isValid()).isFalse();
                  }
                }),
        argumentSet(
            "view stream",
            (Consumer<List<Message>>)
                messages ->
                    messages.subList(0, 2).stream().forEach(SqsTracingListTest::processing)),
        argumentSet(
            "reverse",
            (Consumer<List<Message>>)
                messages -> {
                  ListIterator<Message> iterator = messages.listIterator(messages.size());
                  while (iterator.hasPrevious()) {
                    processing(iterator.previous());
                  }
                }));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "iterator",
        "listIterator",
        "indexed listIterator",
        "spliterator",
        "view iterator",
        "view listIterator",
        "view spliterator",
        "nested view iterator"
      })
  void discardedTraversalHandleConsumesSharedChance(String traversal) {
    List<Message> messages = tracingMessages();
    Object unusedHandle;
    switch (traversal) {
      case "iterator":
        unusedHandle = messages.iterator();
        break;
      case "listIterator":
        unusedHandle = messages.listIterator();
        break;
      case "indexed listIterator":
        unusedHandle = messages.listIterator(1);
        break;
      case "spliterator":
        unusedHandle = messages.spliterator();
        break;
      case "view iterator":
        unusedHandle = messages.subList(0, 2).iterator();
        break;
      case "view listIterator":
        unusedHandle = messages.subList(0, 2).listIterator();
        break;
      case "view spliterator":
        unusedHandle = messages.subList(0, 2).spliterator();
        break;
      default:
        unusedHandle = messages.subList(0, 2).subList(0, 1).iterator();
    }
    assertThat(unusedHandle).isNotNull();

    messages.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    messages
        .subList(0, 2)
        .spliterator()
        .forEachRemaining(
            message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    assertThat(testing.spans()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("traversals")
  void laterTraversalsOfResponseAndViewsDoNotTrace(Consumer<List<Message>> firstTraversal) {
    List<Message> messages = tracingMessages();
    firstTraversal.accept(messages);
    assertThat(testing.spans()).hasSize(2);

    messages.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    messages
        .subList(0, 2)
        .iterator()
        .forEachRemaining(
            message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    messages
        .subList(0, 2)
        .subList(0, 1)
        .spliterator()
        .forEachRemaining(
            message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void firstHandleRemainsEligibleAfterLaterHandleAcquisition() {
    List<Message> messages = tracingMessages();
    Iterator<Message> first = messages.iterator();
    Iterator<Message> later = messages.subList(0, 2).iterator();
    later.forEachRemaining(
        message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    first.forEachRemaining(SqsTracingListTest::processing);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void nextWithoutHasNextFinishesPreviousInvocation() {
    ListIterator<Message> iterator = tracingMessages().listIterator();
    Context previous = Context.current();
    iterator.next();
    iterator.next();
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);

    assertThat(Context.current()).isSameAs(previous);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void listIteratorMutationsRemainVisibleInViews() {
    List<Message> messages = tracingMessages();
    ListIterator<Message> iterator = messages.subList(0, 2).listIterator();
    iterator.next();
    Message replacement = new Message().withMessageId("replacement");
    Message added = new Message().withMessageId("added");
    iterator.set(replacement);
    iterator.add(added);
    iterator.next();
    iterator.remove();
    assertThat(iterator.hasNext()).isFalse();
    assertThat(messages.toArray()).containsExactly(replacement, added);
    messages.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void nonProcessingViewOperationsDoNotCreateSpansOrChangeContext() {
    List<Message> messages = tracingMessages();
    List<Message> view = messages.subList(0, 1);
    List<Message> equivalentView = messages.subList(0, 1);
    List<Message> equivalentMessages = asList(messages.get(0), messages.get(1));
    Context previous = Context.current();

    assertThat(view.contains(messages.get(0))).isTrue();
    assertThat(view.containsAll(equivalentView)).isTrue();
    assertThat(view.equals(equivalentView)).isTrue();
    assertThat(view.hashCode()).isEqualTo(equivalentView.hashCode());
    assertThat(view.toString()).isEqualTo(equivalentView.toString());
    assertThat(messages.equals(equivalentMessages)).isTrue();
    assertThat(messages.hashCode()).isEqualTo(equivalentMessages.hashCode());
    assertThat(messages.toString()).isEqualTo(equivalentMessages.toString());
    view.clear();

    assertThat(Context.current()).isSameAs(previous);
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void callbacksValidateNullActionsForEmptyLists() {
    List<Message> messages = tracingMessages().subList(0, 0);
    assertThatThrownBy(() -> messages.forEach(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> messages.iterator().forEachRemaining(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> messages.spliterator().tryAdvance(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> messages.spliterator().forEachRemaining(null))
        .isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"forEach", "iterator", "spliterator", "view"})
  void callbackFailureFinishesSpanAndRestoresContext(String traversal) {
    List<Message> messages = tracingMessages();
    Context previous = Context.current();
    IllegalStateException failure = new IllegalStateException("processing failed");
    Consumer<Message> action =
        message -> {
          processing(message);
          throw failure;
        };
    assertThatThrownBy(
            () -> {
              switch (traversal) {
                case "iterator":
                  messages.iterator().forEachRemaining(action);
                  break;
                case "spliterator":
                  messages.spliterator().tryAdvance(action);
                  break;
                case "view":
                  messages.subList(0, 2).forEach(action);
                  break;
                default:
                  messages.forEach(action);
              }
            })
        .isSameAs(failure);
    assertThat(Context.current()).isSameAs(previous);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("process").hasException(failure)));
  }

  @Test
  void splitCallbackFailureFinishesSpanWithoutDisablingSibling() {
    Spliterator<Message> first = tracingMessages().spliterator();
    Spliterator<Message> second = requireNonNull(first.trySplit());
    Context previous = Context.current();
    IllegalStateException failure = new IllegalStateException("split failed");

    assertThatThrownBy(
            () ->
                second.tryAdvance(
                    message -> {
                      processing(message);
                      throw failure;
                    }))
        .isSameAs(failure);
    assertThat(Context.current()).isSameAs(previous);
    first.forEachRemaining(SqsTracingListTest::processing);
    assertThat(Context.current()).isSameAs(previous);
    assertThat(testing.spans()).hasSize(2);
    assertThat(testing.spans()).anySatisfy(span -> assertThat(span).hasException(failure));
  }

  @Test
  void nestedTraversalRestoresOuterScopeAndCapturedContext() {
    testing.runWithSpan(
        "parent",
        () -> {
          Context parent = Context.current().with(APPLICATION_KEY, "application");
          List<Message> outer = tracingMessages(parent, true);
          outer.forEach(
              message -> {
                Context outerContext = Context.current();
                List<Message> inner = tracingMessages(parent, true);
                inner.forEach(
                    nested -> {
                      assertThat(Span.current().getSpanContext())
                          .isNotEqualTo(Span.fromContext(outerContext).getSpanContext());
                      assertThat(Context.current().get(APPLICATION_KEY)).isEqualTo("application");
                    });
                assertThat(Context.current()).isSameAs(outerContext);
              });
        });
    assertThat(testing.spans()).hasSize(7);
    SpanData parent =
        testing.spans().stream().filter(span -> span.getName().equals("parent")).findFirst().get();
    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("process"))
        .allSatisfy(span -> assertThat(span).hasParent(parent));
  }

  @Test
  void viewProcessingOwnershipConsumesOnlyEligibleTraversal() {
    List<Message> messages = tracingMessages();
    List<Message> selected = messages.subList(0, 1);
    List<Message> sibling = messages.subList(1, 2);
    ListIterator<Message> selectedIterator = selected.listIterator();
    SqsProcessTracing.markProcessingOwnedOutsideSqsSdk(selected);

    selectedIterator.forEachRemaining(
        message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    messages.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    sibling.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());

    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void parentProcessingOwnershipConsumesOnlyEligibleTraversal() {
    List<Message> messages = tracingMessages();
    List<Message> view = messages.subList(0, 1);
    ListIterator<Message> messagesIterator = messages.listIterator();
    SqsProcessTracing.markProcessingOwnedOutsideSqsSdk(messages);

    messagesIterator.forEachRemaining(
        message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    view.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());

    assertThat(testing.spans()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void processingOwnershipOnlyAppliesToMarkedList(boolean markRoot) {
    List<Message> messages = tracingMessages();
    List<Message> view = messages.subList(0, 1);
    SqsProcessTracing.markProcessingOwnedOutsideSqsSdk(markRoot ? messages : view);

    (markRoot ? view : messages).forEach(SqsTracingListTest::processing);

    assertThat(testing.spans()).hasSize(markRoot ? 1 : 2);
  }

  @Test
  void unsupportedCopiedListDoesNotMarkResponseOwned() {
    List<Message> messages = tracingMessages();
    SqsProcessTracing.markProcessingOwnedOutsideSqsSdk(new ArrayList<>(messages));
    messages.forEach(SqsTracingListTest::processing);
    messages.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    assertThat(testing.spans()).hasSize(2);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void disabledInstrumenterPreservesCallbacks(boolean enabled) {
    List<Message> messages = tracingMessages(Context.root(), enabled);
    List<Message> visited = new ArrayList<>();
    messages.forEach(
        message -> {
          visited.add(message);
          assertThat(Span.current().getSpanContext().isValid()).isEqualTo(enabled);
        });
    assertThat(visited.toArray()).containsExactly(messages.toArray());
    assertThat(testing.spans()).hasSize(enabled ? 2 : 0);
  }

  @Test
  void explicitSuppressionConsumesFirstTraversal() {
    List<Message> messages = tracingMessages();
    InstrumentationUtil.suppressInstrumentation(
        () ->
            messages.forEach(
                message -> assertThat(Span.current().getSpanContext().isValid()).isFalse()));
    assertThat(testing.spans()).isEmpty();
    messages.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void splitTraversalPreservesCharacteristicsAndCompletesOnEachThread() {
    Spliterator<Message> first = tracingMessages().subList(0, 2).spliterator();
    long size = first.estimateSize();
    int characteristics = first.characteristics();
    Spliterator<Message> second = requireNonNull(first.trySplit());
    assertThat(first.estimateSize() + second.estimateSize()).isEqualTo(size);
    assertThat(first.characteristics()).isEqualTo(characteristics);
    assertThat(second.characteristics()).isEqualTo(characteristics);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture.allOf(
              CompletableFuture.runAsync(() -> process(first), executor),
              CompletableFuture.runAsync(() -> process(second), executor))
          .join();
    } finally {
      executor.shutdownNow();
    }
    assertThat(testing.spans()).hasSize(2);
  }

  private static void process(Spliterator<Message> messages) {
    Context previous = Context.current();
    messages.forEachRemaining(SqsTracingListTest::processing);
    assertThat(Context.current()).isSameAs(previous);
  }

  private static void processing(Message message) {
    assertThat(Span.current().getSpanContext().isValid()).isTrue();
  }

  private static SdkInternalList<Message> tracingMessages() {
    return tracingMessages(Context.root(), true);
  }

  private static SdkInternalList<Message> tracingMessages(Context parent, boolean enabled) {
    return tracingMessages(
        parent,
        enabled,
        asList(new Message().withMessageId("first"), new Message().withMessageId("second")));
  }

  private static SdkInternalList<Message> tracingMessages(
      Context parent, boolean enabled, List<Message> messages) {
    Instrumenter<SqsProcessRequest, Response<?>> instrumenter =
        Instrumenter.<SqsProcessRequest, Response<?>>builder(
                testing.getOpenTelemetry(), "test", request -> "process")
            .setEnabled(enabled)
            .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    return TracingList.wrap(
        messages,
        instrumenter,
        new DefaultRequest<>("AmazonSQS"),
        new Response<>(null, null),
        parent);
  }
}
