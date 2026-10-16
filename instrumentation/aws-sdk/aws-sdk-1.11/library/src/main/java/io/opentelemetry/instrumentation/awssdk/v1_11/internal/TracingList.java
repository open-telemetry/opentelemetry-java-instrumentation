/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.internal.SdkInternalList;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.Message;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

class TracingList extends SdkInternalList<Message> {
  private static final long serialVersionUID = 1L;

  private final transient Instrumenter<SqsProcessRequest, Response<?>> instrumenter;
  private final transient Request<?> request;
  private final transient Response<?> response;
  @Nullable private final transient Context processParentContext;
  private boolean firstIterator = true;

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
    Iterator<Message> iterator = super.iterator();
    if (firstIterator && !inAwsClient()) {
      firstIterator = false;
      return TracingIterator.wrap(iterator, this);
    }
    return iterator;
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

  boolean isProcessingOwnedOutsideSqsSdk() {
    return SqsProcessTracing.isProcessingOwnedOutsideSqsSdk(this);
  }

  @Nullable
  static SdkInternalList<?> processingOwner(List<?> messages) {
    return messages instanceof TracingList ? (TracingList) messages : null;
  }

  @Override
  public void forEach(Consumer<? super Message> action) {
    iterator().forEachRemaining(action);
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

  private static class CallerClass extends SecurityManager {
    static final CallerClass INSTANCE = new CallerClass();

    @Override
    public Class<?>[] getClassContext() {
      return super.getClassContext();
    }
  }
}
