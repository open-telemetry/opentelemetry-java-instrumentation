/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.amazonaws.DefaultRequest;
import com.amazonaws.Response;
import com.amazonaws.internal.SdkInternalList;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
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
  @MethodSource("tracedTraversals")
  void observableTraversalFinishesProcessSpans(Consumer<List<Message>> traversal) {
    Context previous = Context.current();
    traversal.accept(tracingMessages());

    assertThat(Context.current()).isSameAs(previous);
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("process").hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("process").hasNoParent()));
  }

  private static Stream<Arguments> tracedTraversals() {
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
            "iterator forEachRemaining",
            (Consumer<List<Message>>)
                messages -> messages.iterator().forEachRemaining(SqsTracingListTest::processing)));
  }

  @Test
  void discardedFirstIteratorConsumesTracingOpportunity() {
    List<Message> messages = tracingMessages();
    Iterator<Message> unused = messages.iterator();
    assertThat(unused).isNotNull();

    messages.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    assertThat(testing.spans()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("tracedTraversals")
  void laterTraversalsOfResponseDoNotTrace(Consumer<List<Message>> firstTraversal) {
    List<Message> messages = tracingMessages();
    firstTraversal.accept(messages);
    assertThat(testing.spans()).hasSize(2);

    Consumer<Message> untraced =
        message -> assertThat(Span.current().getSpanContext().isValid()).isFalse();
    messages.forEach(untraced);
    messages.iterator().forEachRemaining(untraced);
    messages.spliterator().forEachRemaining(untraced);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void onlyFirstIteratorHandleTraces() {
    List<Message> messages = tracingMessages();
    Iterator<Message> first = messages.iterator();
    Iterator<Message> later = messages.iterator();
    later.forEachRemaining(
        message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    first.forEachRemaining(SqsTracingListTest::processing);
    assertThat(testing.spans()).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("untracedTraversals")
  void otherTraversalApisDoNotTraceOrConsumeFirstIterator(Consumer<List<Message>> traversal) {
    List<Message> messages = tracingMessages();
    Context previous = Context.current();
    traversal.accept(messages);

    assertThat(Context.current()).isSameAs(previous);
    assertThat(testing.spans()).isEmpty();
    messages.forEach(SqsTracingListTest::processing);
    assertThat(testing.spans()).hasSize(2);
  }

  private static Stream<Arguments> untracedTraversals() {
    Consumer<Message> untraced =
        message -> assertThat(Span.current().getSpanContext().isValid()).isFalse();
    return Stream.of(
        argumentSet(
            "listIterator",
            (Consumer<List<Message>>)
                messages -> messages.listIterator().forEachRemaining(untraced)),
        argumentSet(
            "indexed listIterator",
            (Consumer<List<Message>>)
                messages -> messages.listIterator(1).forEachRemaining(untraced)),
        argumentSet(
            "reverse listIterator",
            (Consumer<List<Message>>)
                messages -> {
                  ListIterator<Message> iterator = messages.listIterator(messages.size());
                  while (iterator.hasPrevious()) {
                    untraced.accept(iterator.previous());
                  }
                }),
        argumentSet(
            "spliterator",
            (Consumer<List<Message>>)
                messages -> messages.spliterator().forEachRemaining(untraced)),
        argumentSet(
            "tryAdvance",
            (Consumer<List<Message>>)
                messages -> {
                  Spliterator<Message> spliterator = messages.spliterator();
                  while (spliterator.tryAdvance(untraced)) {}
                }),
        argumentSet(
            "split spliterator",
            (Consumer<List<Message>>)
                messages -> {
                  Spliterator<Message> first = messages.spliterator();
                  Spliterator<Message> second = first.trySplit();
                  assertThat(second).isNotNull();
                  first.forEachRemaining(untraced);
                  second.forEachRemaining(untraced);
                }),
        argumentSet(
            "stream", (Consumer<List<Message>>) messages -> messages.stream().forEach(untraced)));
  }

  @Test
  void nextWithoutHasNextFinishesPreviousInvocation() {
    Iterator<Message> iterator = tracingMessages().iterator();
    Context previous = Context.current();
    iterator.next();
    iterator.next();
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);

    assertThat(Context.current()).isSameAs(previous);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void sublistMutationsRemainVisibleInResponse() {
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
    assertThat(testing.spans()).isEmpty();
    messages.forEach(SqsTracingListTest::processing);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void nonProcessingViewOperationsDoNotCreateSpansOrChangeContext() {
    List<Message> messages = tracingMessages();
    List<Message> view = messages.subList(0, 1);
    List<Message> equivalentView = messages.subList(0, 1);
    Context previous = Context.current();

    assertThat(view.contains(messages.get(0))).isTrue();
    assertThat(view.containsAll(equivalentView)).isTrue();
    assertThat(view.equals(equivalentView)).isTrue();
    assertThat(view.hashCode()).isEqualTo(equivalentView.hashCode());
    assertThat(view.toString()).isEqualTo(equivalentView.toString());
    view.clear();

    assertThat(Context.current()).isSameAs(previous);
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void callbacksValidateNullActionsForEmptyLists() {
    List<Message> messages = tracingMessages(Context.root(), true, new ArrayList<>());
    assertThatThrownBy(() -> messages.forEach(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> messages.iterator().forEachRemaining(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> messages.spliterator().tryAdvance(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> messages.spliterator().forEachRemaining(null))
        .isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"forEach", "iterator"})
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
                default:
                  messages.forEach(action);
              }
            })
        .isSameAs(failure);
    assertThat(Context.current()).isSameAs(previous);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span
                        .hasName("process")
                        .hasException(emitExceptionAsSpanEvents() ? failure : null)));
  }

  @Test
  void nestedTraversalRestoresOuterScopeAndCapturedContext() {
    Instrumenter<SqsProcessRequest, Response<?>> instrumenter = keyedProcessInstrumenter();
    testing.runWithSpan(
        "parent",
        () -> {
          Context parent = Context.current().with(APPLICATION_KEY, "application");
          List<Message> outer = tracingMessages(parent, instrumenter);
          outer.forEach(
              message -> {
                Context outerContext = Context.current();
                List<Message> inner = tracingMessages(parent, instrumenter);
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
  void nestedProcessFromCapturedParentIsSuppressed() {
    Instrumenter<SqsProcessRequest, Response<?>> instrumenter = keyedProcessInstrumenter();
    List<Message> messages = tracingMessages(Context.root(), instrumenter);
    Context previous = Context.current();

    messages.forEach(
        message -> {
          Context processContext = Context.current();
          assertThat(SpanKey.CONSUMER_PROCESS.fromContextOrNull(processContext)).isNotNull();
          tracingMessages(processContext, instrumenter)
              .forEach(nested -> assertThat(Context.current()).isSameAs(processContext));
          assertThat(Context.current()).isSameAs(processContext);
        });

    assertThat(Context.current()).isSameAs(previous);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void abandonedIteratorDoesNotSuppressUnrelatedResponse() {
    Instrumenter<SqsProcessRequest, Response<?>> instrumenter = keyedProcessInstrumenter();
    Iterator<Message> abandoned = tracingMessages(Context.root(), instrumenter).iterator();
    List<Message> unrelated = tracingMessages(Context.root(), instrumenter);
    Context previous = Context.current();
    abandoned.next();
    Context ambient = Context.current();
    assertThat(SpanKey.CONSUMER_PROCESS.fromContextOrNull(ambient)).isNotNull();

    unrelated.forEach(
        message -> {
          assertThat(Span.current().getSpanContext())
              .isNotEqualTo(Span.fromContext(ambient).getSpanContext());
          assertThat(Span.current().getSpanContext().isValid()).isTrue();
        });

    assertThat(Context.current()).isSameAs(ambient);
    assertThat(abandoned.hasNext()).isTrue();
    assertThat(Context.current()).isSameAs(previous);
    assertThat(testing.spans()).hasSize(3);
    assertThat(testing.spans()).allSatisfy(span -> assertThat(span).hasNoParent());
  }

  @ParameterizedTest
  @MethodSource("sublistTraversals")
  void sublistTraversalDoesNotTraceOrDisableResponse(Consumer<List<Message>> traversal) {
    List<Message> messages = tracingMessages();
    Context previous = Context.current();
    traversal.accept(messages.subList(0, 2));

    assertThat(Context.current()).isSameAs(previous);
    assertThat(testing.spans()).isEmpty();
    messages.forEach(SqsTracingListTest::processing);
    assertThat(testing.spans()).hasSize(2);
  }

  private static Stream<Arguments> sublistTraversals() {
    Consumer<Message> untraced =
        message -> assertThat(Span.current().getSpanContext().isValid()).isFalse();
    return Stream.of(
        argumentSet("forEach", (Consumer<List<Message>>) view -> view.forEach(untraced)),
        argumentSet(
            "iterator",
            (Consumer<List<Message>>) view -> view.iterator().forEachRemaining(untraced)),
        argumentSet(
            "listIterator",
            (Consumer<List<Message>>) view -> view.listIterator().forEachRemaining(untraced)),
        argumentSet(
            "spliterator",
            (Consumer<List<Message>>) view -> view.spliterator().forEachRemaining(untraced)),
        argumentSet(
            "nested sublist",
            (Consumer<List<Message>>) view -> view.subList(0, 1).forEach(untraced)),
        argumentSet("stream", (Consumer<List<Message>>) view -> view.stream().forEach(untraced)),
        argumentSet(
            "split spliterator",
            (Consumer<List<Message>>)
                view -> {
                  Spliterator<Message> first = view.spliterator();
                  Spliterator<Message> second = requireNonNull(first.trySplit());
                  first.forEachRemaining(untraced);
                  second.forEachRemaining(untraced);
                }));
  }

  @Test
  void markingSublistDoesNotClaimResponse() {
    List<Message> messages = tracingMessages();
    List<Message> view = messages.subList(0, 1);
    SqsProcessTracing.markProcessingOwnedOutsideSqsSdk(view);

    view.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    messages.forEach(SqsTracingListTest::processing);
    assertThat(testing.spans()).hasSize(2);
  }

  @Test
  void processingOwnershipSuppressesResponse() {
    List<Message> messages = tracingMessages();
    SqsProcessTracing.markProcessingOwnedOutsideSqsSdk(messages);
    messages.forEach(message -> assertThat(Span.current().getSpanContext().isValid()).isFalse());
    assertThat(testing.spans()).isEmpty();
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
      Context parent, Instrumenter<SqsProcessRequest, Response<?>> instrumenter) {
    return TracingList.wrap(
        asList(new Message().withMessageId("first"), new Message().withMessageId("second")),
        instrumenter,
        new DefaultRequest<>(
            new ReceiveMessageRequest().withQueueUrl("http://localhost/queue"), "AmazonSQS"),
        new Response<>(null, null),
        parent);
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

  private static Instrumenter<SqsProcessRequest, Response<?>> keyedProcessInstrumenter() {
    return Instrumenter.<SqsProcessRequest, Response<?>>builder(
            testing.getOpenTelemetry(), "test", request -> "process")
        .addAttributesExtractor(
            MessagingAttributesExtractor.create(
                new SqsProcessRequestAttributesGetter(), MessagingOperationType.PROCESS, "process"))
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }
}
