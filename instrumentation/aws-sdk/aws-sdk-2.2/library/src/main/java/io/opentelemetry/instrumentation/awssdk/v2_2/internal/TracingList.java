/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class TracingList extends ArrayList<Message> {
  private static final long serialVersionUID = 1L;

  private final Instrumenter<SqsProcessRequest, Response> instrumenter;
  private final ExecutionAttributes request;
  private final Response response;
  private final TracingExecutionInterceptor config;
  private final IdentityHashMap<Message, SqsMessage> tracingMessages;
  @Nullable private final Context processParentContext;
  private volatile boolean processingOwnedOutsideSqsSdk;

  public static TracingList wrap(
      List<Message> messages,
      List<SqsMessage> tracingMessages,
      Instrumenter<SqsProcessRequest, Response> instrumenter,
      ExecutionAttributes request,
      Response response,
      TracingExecutionInterceptor config,
      @Nullable Context processParentContext) {
    return new TracingList(
        messages, tracingMessages, instrumenter, request, response, config, processParentContext);
  }

  private TracingList(
      List<Message> messages,
      List<SqsMessage> tracingMessages,
      Instrumenter<SqsProcessRequest, Response> instrumenter,
      ExecutionAttributes request,
      Response response,
      TracingExecutionInterceptor config,
      @Nullable Context processParentContext) {
    super(messages);
    this.instrumenter = instrumenter;
    this.request = request;
    this.response = response;
    this.config = config;
    this.processParentContext = processParentContext;
    this.tracingMessages = new IdentityHashMap<>();
    for (int i = 0; i < messages.size(); i++) {
      this.tracingMessages.put(messages.get(i), tracingMessages.get(i));
    }
  }

  public void markProcessingOwnedOutsideSqsSdk() {
    processingOwnedOutsideSqsSdk = true;
  }

  boolean isProcessingOwnedOutsideSqsSdk() {
    return processingOwnedOutsideSqsSdk;
  }

  @Override
  public Iterator<Message> iterator() {
    return tracingIterator(super.iterator());
  }

  @Override
  public ListIterator<Message> listIterator() {
    return tracingListIterator(super.listIterator());
  }

  @Override
  public ListIterator<Message> listIterator(int index) {
    return tracingListIterator(super.listIterator(index));
  }

  @Override
  public Spliterator<Message> spliterator() {
    return tracingSpliterator(super.spliterator());
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof List)) {
      return false;
    }
    return equalsWithoutTracing(this, (List<?>) object);
  }

  @Override
  public int hashCode() {
    return hashCodeWithoutTracing(this);
  }

  @Override
  public String toString() {
    return toStringWithoutTracing(this);
  }

  private static boolean equalsWithoutTracing(List<?> left, List<?> right) {
    if (left == right) {
      return true;
    }
    int size = left.size();
    if (size != right.size()) {
      return false;
    }
    for (int i = 0; i < size; i++) {
      if (!Objects.equals(left.get(i), right.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static int hashCodeWithoutTracing(List<?> list) {
    int hashCode = 1;
    for (int i = 0; i < list.size(); i++) {
      Object element = list.get(i);
      hashCode = 31 * hashCode + (element == null ? 0 : element.hashCode());
    }
    return hashCode;
  }

  private static String toStringWithoutTracing(List<?> list) {
    StringBuilder result = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
      if (i != 0) {
        result.append(", ");
      }
      Object element = list.get(i);
      result.append(element == list ? "(this Collection)" : element);
    }
    return result.append(']').toString();
  }

  private Iterator<Message> tracingIterator(Iterator<Message> delegateIterator) {
    return processingOwnedOutsideSqsSdk
        ? delegateIterator
        : TracingIterator.wrap(delegateIterator, this);
  }

  private ListIterator<Message> tracingListIterator(ListIterator<Message> delegateIterator) {
    return processingOwnedOutsideSqsSdk
        ? delegateIterator
        : TracingListIterator.wrap(delegateIterator, this);
  }

  private Spliterator<Message> tracingSpliterator(Spliterator<Message> delegateSpliterator) {
    return processingOwnedOutsideSqsSdk
        ? delegateSpliterator
        : TracingSpliterator.wrap(delegateSpliterator, this);
  }

  private Consumer<? super Message> tracingAction(Consumer<? super Message> action) {
    requireNonNull(action);
    return processingOwnedOutsideSqsSdk
        ? action
        : message -> TracingIterator.processCallback(this, message, action);
  }

  @Override
  public void forEach(Consumer<? super Message> action) {
    super.forEach(tracingAction(action));
  }

  public Instrumenter<SqsProcessRequest, Response> getInstrumenter() {
    return instrumenter;
  }

  public ExecutionAttributes getRequest() {
    return request;
  }

  public Response getResponse() {
    return response;
  }

  public TracingExecutionInterceptor getConfig() {
    return config;
  }

  @Nullable
  public SqsMessage getTracingMessage(Message message) {
    return tracingMessages.get(message);
  }

  @Nullable
  public Context getProcessParentContext() {
    return processParentContext;
  }
}
