/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.javaagent.instrumentation.hystrix.HystrixTracer.tracer;

import com.netflix.hystrix.HystrixInvokableInfo;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.rxjava.TracedOnSubscribe;
import rx.Observable;

public class HystrixOnSubscribe<T> extends TracedOnSubscribe<T> {
  private static final String OPERATION_NAME = "hystrix.cmd";

  private final HystrixInvokableInfo<?> command;
  private final String methodName;

  public HystrixOnSubscribe(
      Observable<T> originalObservable, HystrixInvokableInfo<?> command, String methodName) {
    super(originalObservable, OPERATION_NAME, tracer(), INTERNAL);

    this.command = command;
    this.methodName = methodName;
  }

  @Override
  protected void decorateSpan(Span span) {
    tracer().onCommand(span, command, methodName);
  }
}
