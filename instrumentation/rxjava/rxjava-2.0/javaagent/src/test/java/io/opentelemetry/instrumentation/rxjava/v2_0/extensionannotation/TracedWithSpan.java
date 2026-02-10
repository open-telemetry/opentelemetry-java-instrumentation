/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v2_0.extensionannotation;

import io.opentelemetry.instrumentation.rxjava.v2_0.AbstractTracedWithSpan;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.parallel.ParallelFlowable;
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
