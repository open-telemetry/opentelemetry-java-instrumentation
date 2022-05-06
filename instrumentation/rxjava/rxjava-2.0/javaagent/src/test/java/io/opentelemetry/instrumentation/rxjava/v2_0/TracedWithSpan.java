/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v2_0;

import io.opentelemetry.extension.annotations.WithSpan;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.parallel.ParallelFlowable;
import org.reactivestreams.Publisher;

public class TracedWithSpan {

  @WithSpan
  public Completable completable(Completable source) {
    return source;
  }

  @WithSpan
  public Maybe<String> maybe(Maybe<String> source) {
    return source;
  }

  @WithSpan
  public Single<String> single(Single<String> source) {
    return source;
  }

  @WithSpan
  public Observable<String> observable(Observable<String> source) {
    return source;
  }

  @WithSpan
  public Flowable<String> flowable(Flowable<String> source) {
    return source;
  }

  @WithSpan
  public ParallelFlowable<String> parallelFlowable(ParallelFlowable<String> source) {
    return source;
  }

  @WithSpan
  public Publisher<String> publisher(Publisher<String> source) {
    return source;
  }
}
