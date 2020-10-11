/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.couchbase.v2_0;

import static io.opentelemetry.instrumentation.auto.couchbase.v2_0.CouchbaseClientTracer.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import io.opentelemetry.instrumentation.auto.rxjava.TracedOnSubscribe;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;
import rx.Observable;

public class CouchbaseOnSubscribe extends TracedOnSubscribe {
  private final String bucket;
  private final String query;

  public static CouchbaseOnSubscribe create(
      Observable originalObservable, String bucket, Method method) {
    Class<?> declaringClass = method.getDeclaringClass();
    String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    return new CouchbaseOnSubscribe(originalObservable, bucket, className + "." + method.getName());
  }

  public static CouchbaseOnSubscribe create(
      Observable originalObservable, String bucket, String query) {
    return new CouchbaseOnSubscribe(originalObservable, bucket, query);
  }

  private CouchbaseOnSubscribe(Observable originalObservable, String bucket, String query) {
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
