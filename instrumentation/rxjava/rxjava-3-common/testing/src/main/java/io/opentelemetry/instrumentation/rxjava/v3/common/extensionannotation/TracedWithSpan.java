/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v3.common.extensionannotation;

import io.opentelemetry.instrumentation.rxjava.v3.common.AbstractTracedWithSpan;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.parallel.ParallelFlowable;
import org.reactivestreams.Publisher;

@SuppressWarnings("deprecation") // testing instrumentation of deprecated class
public class TracedWithSpan extends AbstractTracedWithSpan {

  @Override
  @io.opentelemetry.extension.annotations.WithSpan
  public Completable completable(Completable source) {
    return source;
  }

  @Override
  @io.opentelemetry.extension.annotations.WithSpan
  public Maybe<String> maybe(Maybe<String> source) {
    return source;
  }

  @Override
  @io.opentelemetry.extension.annotations.WithSpan
  public Single<String> single(Single<String> source) {
    return source;
  }

  @Override
  @io.opentelemetry.extension.annotations.WithSpan
  public Observable<String> observable(Observable<String> source) {
    return source;
  }

  @Override
  @io.opentelemetry.extension.annotations.WithSpan
  public Flowable<String> flowable(Flowable<String> source) {
    return source;
  }

  @Override
  @io.opentelemetry.extension.annotations.WithSpan
  public ParallelFlowable<String> parallelFlowable(ParallelFlowable<String> source) {
    return source;
  }

  @Override
  @io.opentelemetry.extension.annotations.WithSpan
  public Publisher<String> publisher(Publisher<String> source) {
    return source;
  }
}
