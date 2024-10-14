/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
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
  private final Context receiveContext;
  private boolean firstIterator = true;

  private TracingList(
      List<Message> list,
      Instrumenter<SqsProcessRequest, Response> instrumenter,
      ExecutionAttributes request,
      Response response,
      TracingExecutionInterceptor config,
      Context receiveContext) {
    super(list);
    this.instrumenter = instrumenter;
    this.request = request;
    this.response = response;
    this.config = config;
    this.receiveContext = receiveContext;
  }

  public static TracingList wrap(
      List<Message> list,
      Instrumenter<SqsProcessRequest, Response> instrumenter,
      ExecutionAttributes request,
      Response response,
      TracingExecutionInterceptor config,
      Context receiveContext) {
    return new TracingList(list, instrumenter, request, response, config, receiveContext);
  }

  public void disableTracing() {
    // only the first call to iterator() is traced
    firstIterator = false;
  }

  @Override
  public Iterator<Message> iterator() {
    Iterator<Message> it;
    // We should only return one iterator with tracing.
    // However, this is not thread-safe, but usually the first (hopefully only) traversal of
    // List is performed in the same thread that called receiveMessage()
    if (firstIterator) {
      it =
          TracingIterator.wrap(
              super.iterator(), instrumenter, request, response, config, receiveContext);
      firstIterator = false;
    } else {
      it = super.iterator();
    }

    return it;
  }

  @Override
  public void forEach(Consumer<? super Message> action) {
    for (Message message : this) {
      action.accept(message);
    }
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

  public Context getReceiveContext() {
    return receiveContext;
  }
}
