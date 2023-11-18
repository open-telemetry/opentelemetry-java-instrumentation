/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.sqs.model.Message;

class TracingList extends ArrayList<Message> {
  private static final long serialVersionUID = 1L;

  private final Instrumenter<SqsProcessRequest, Void> instrumenter;
  private final ExecutionAttributes request;
  private final TracingExecutionInterceptor config;
  private final Context receiveContext;
  private boolean firstIterator = true;

  private TracingList(
      List<Message> list,
      Instrumenter<SqsProcessRequest, Void> instrumenter,
      ExecutionAttributes request,
      TracingExecutionInterceptor config,
      Context receiveContext) {
    super(list);
    this.instrumenter = instrumenter;
    this.request = request;
    this.config = config;
    this.receiveContext = receiveContext;
  }

  public static TracingList wrap(
      List<Message> list,
      Instrumenter<SqsProcessRequest, Void> instrumenter,
      ExecutionAttributes request,
      TracingExecutionInterceptor config,
      Context receiveContext) {
    return new TracingList(list, instrumenter, request, config, receiveContext);
  }

  @Override
  public Iterator<Message> iterator() {
    Iterator<Message> it;
    // We should only return one iterator with tracing.
    // However, this is not thread-safe, but usually the first (hopefully only) traversal of
    // List is performed in the same thread that called receiveMessage()
    if (firstIterator) {
      it = TracingIterator.wrap(super.iterator(), instrumenter, request, config, receiveContext);
      firstIterator = false;
    } else {
      it = super.iterator();
    }

    return it;
  }
}
