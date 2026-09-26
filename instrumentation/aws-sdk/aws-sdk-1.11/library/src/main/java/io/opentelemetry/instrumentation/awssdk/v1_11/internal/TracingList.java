/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static java.util.Objects.requireNonNull;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.internal.SdkInternalList;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.Message;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import javax.annotation.Nullable;

class TracingList extends SdkInternalList<Message> {
  private static final long serialVersionUID = 1L;

  private final transient Instrumenter<SqsProcessRequest, Response<?>> instrumenter;
  private final transient Request<?> request;
  private final transient Response<?> response;
  @Nullable private final transient Context processParentContext;
  private final transient ProcessingOwnership processingOwnership = new ProcessingOwnership();

  static SdkInternalList<Message> wrap(
      List<Message> messages,
      Instrumenter<SqsProcessRequest, Response<?>> instrumenter,
      Request<?> request,
      Response<?> response,
      @Nullable Context processParentContext) {
    return new TracingList(messages, instrumenter, request, response, processParentContext);
  }

  private TracingList(
      List<Message> messages,
      Instrumenter<SqsProcessRequest, Response<?>> instrumenter,
      Request<?> request,
      Response<?> response,
      @Nullable Context processParentContext) {
    super(messages);
    this.instrumenter = instrumenter;
    this.request = request;
    this.response = response;
    this.processParentContext = processParentContext;
  }

  @Override
  public Iterator<Message> iterator() {
    return listIterator();
  }

  @Override
  public ListIterator<Message> listIterator() {
    return listIterator(0);
  }

  @Override
  public ListIterator<Message> listIterator(int index) {
    ListIterator<Message> iterator = super.listIterator(index);
    return inAwsClient() ? iterator : TracingIterator.wrap(iterator, this, processingOwnership);
  }

  @Override
  public Spliterator<Message> spliterator() {
    Spliterator<Message> spliterator = super.spliterator();
    return inAwsClient()
        ? spliterator
        : new TracingSpliterator(spliterator, this, processingOwnership);
  }

  Instrumenter<SqsProcessRequest, Response<?>> getInstrumenter() {
    return instrumenter;
  }

  Request<?> getRequest() {
    return request;
  }

  Response<?> getResponse() {
    return response;
  }

  @Nullable
  Context getProcessParentContext() {
    return processParentContext;
  }

  static void markProcessingOwnedOutsideSqsSdk(List<?> messages) {
    if (messages instanceof TracingList) {
      ((TracingList) messages).processingOwnership.ownedOutsideSqsSdk = true;
    }
  }

  @Override
  public void forEach(Consumer<? super Message> action) {
    iterator().forEachRemaining(action);
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (!(object instanceof List)) {
      return false;
    }
    List<?> list = (List<?>) object;
    if (size() != list.size()) {
      return false;
    }
    for (int i = 0; i < size(); i++) {
      if (!Objects.equals(get(i), list.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;
    for (int i = 0; i < size(); i++) {
      Message message = get(i);
      hashCode = 31 * hashCode + (message == null ? 0 : message.hashCode());
    }
    return hashCode;
  }

  @Override
  public String toString() {
    StringBuilder string = new StringBuilder("[");
    for (int i = 0; i < size(); i++) {
      if (i > 0) {
        string.append(", ");
      }
      string.append(get(i));
    }
    return string.append(']').toString();
  }

  private static boolean inAwsClient() {
    for (Class<?> caller : CallerClass.INSTANCE.getClassContext()) {
      if (AmazonSQSClient.class == caller) {
        return true;
      }
    }
    return false;
  }

  private Object writeReplace() {
    // serialize this object to SdkInternalList
    return new SdkInternalList<>(this);
  }

  private static final class TracingSpliterator implements Spliterator<Message> {
    private final Spliterator<Message> delegate;
    private final TracingList tracingList;
    private final ProcessingOwnership processingOwnership;

    private TracingSpliterator(
        Spliterator<Message> delegate,
        TracingList tracingList,
        ProcessingOwnership processingOwnership) {
      this.delegate = delegate;
      this.tracingList = tracingList;
      this.processingOwnership = processingOwnership;
    }

    @Override
    public boolean tryAdvance(Consumer<? super Message> action) {
      requireNonNull(action);
      return delegate.tryAdvance(
          message ->
              TracingIterator.processCallback(tracingList, processingOwnership, message, action));
    }

    @Override
    public void forEachRemaining(Consumer<? super Message> action) {
      requireNonNull(action);
      delegate.forEachRemaining(
          message ->
              TracingIterator.processCallback(tracingList, processingOwnership, message, action));
    }

    @Override
    @Nullable
    public Spliterator<Message> trySplit() {
      Spliterator<Message> split = delegate.trySplit();
      return split == null ? null : new TracingSpliterator(split, tracingList, processingOwnership);
    }

    @Override
    public long estimateSize() {
      return delegate.estimateSize();
    }

    @Override
    public int characteristics() {
      return delegate.characteristics();
    }

    @Override
    @Nullable
    public Comparator<? super Message> getComparator() {
      return delegate.getComparator();
    }
  }

  static final class ProcessingOwnership {
    private volatile boolean ownedOutsideSqsSdk;

    boolean isOwnedOutsideSqsSdk() {
      return ownedOutsideSqsSdk;
    }
  }

  private static class CallerClass extends SecurityManager {
    static final CallerClass INSTANCE = new CallerClass();

    @Override
    public Class<?>[] getClassContext() {
      return super.getClassContext();
    }
  }
}
