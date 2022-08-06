/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v2_0.extensionannotation;

import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.instrumentation.rxjava.v2_0.AbstractTracedWithSpan;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.parallel.ParallelFlowable;
import org.reactivestreams.Publisher;

public class TracedWithSpan extends AbstractTracedWithSpan {

  @Override
  @WithSpan
  public Completable completable(Completable source) {
    return source;
  }

  @Override
  @WithSpan
  public Maybe<String> maybe(Maybe<String> source) {
    return source;
  }

  @Override
  @WithSpan
  public Single<String> single(Single<String> source) {
    return source;
  }

  @Override
  @WithSpan
  public Observable<String> observable(Observable<String> source) {
    return source;
  }

  @Override
  @WithSpan
  public Flowable<String> flowable(Flowable<String> source) {
    return source;
  }

  @Override
  @WithSpan
  public ParallelFlowable<String> parallelFlowable(ParallelFlowable<String> source) {
    return source;
  }

  @Override
  @WithSpan
  public Publisher<String> publisher(Publisher<String> source) {
    return source;
  }
}
