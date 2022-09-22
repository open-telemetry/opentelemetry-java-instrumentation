/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v3.common;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.parallel.ParallelFlowable;
import org.reactivestreams.Publisher;

public abstract class AbstractTracedWithSpan {

  public abstract Completable completable(Completable source);

  public abstract Maybe<String> maybe(Maybe<String> source);

  public abstract Single<String> single(Single<String> source);

  public abstract Observable<String> observable(Observable<String> source);

  public abstract Flowable<String> flowable(Flowable<String> source);

  public abstract ParallelFlowable<String> parallelFlowable(ParallelFlowable<String> source);

  public abstract Publisher<String> publisher(Publisher<String> source);
}
