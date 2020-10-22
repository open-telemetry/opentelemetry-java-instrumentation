/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseClientTracer.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.javaagent.instrumentation.api.db.DbSystem;
import io.opentelemetry.javaagent.instrumentation.rxjava.TracedOnSubscribe;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;
import rx.Observable;

public class CouchbaseOnSubscribe<T> extends TracedOnSubscribe<T> {
  private final String bucket;
  private final String query;

  public static <T> CouchbaseOnSubscribe<T> create(
      Observable<T> originalObservable, String bucket, Method method) {
    Class<?> declaringClass = method.getDeclaringClass();
    String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    return new CouchbaseOnSubscribe<>(
        originalObservable, bucket, className + "." + method.getName());
  }

  public static <T> CouchbaseOnSubscribe<T> create(
      Observable<T> originalObservable, String bucket, Object query) {
    return new CouchbaseOnSubscribe<>(
        originalObservable, bucket, CouchbaseQueryNormalizer.normalize(query));
  }

  private CouchbaseOnSubscribe(Observable<T> originalObservable, String bucket, String query) {
    super(originalObservable, query, TRACER, CLIENT);

    this.bucket = bucket;
    this.query = query;
  }

  @Override
  protected void decorateSpan(Span span) {
    span.setAttribute(SemanticAttributes.DB_SYSTEM, DbSystem.COUCHBASE);
    span.setAttribute(SemanticAttributes.DB_NAME, bucket);
    span.setAttribute(SemanticAttributes.DB_STATEMENT, query);
  }
}
