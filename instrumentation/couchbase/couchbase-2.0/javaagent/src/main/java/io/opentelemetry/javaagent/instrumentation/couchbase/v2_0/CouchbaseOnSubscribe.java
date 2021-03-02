/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer.conventionSpanName;
import static io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseClientTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.rxjava.TracedOnSubscribe;
import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementInfo;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
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
    String operation = className + "." + method.getName();
    return new CouchbaseOnSubscribe<>(originalObservable, operation, bucket, operation);
  }

  public static <T> CouchbaseOnSubscribe<T> create(
      Observable<T> originalObservable, String bucket, Object query) {
    SqlStatementInfo statement = CouchbaseQuerySanitizer.sanitize(query);
    String spanName =
        conventionSpanName(
            bucket, statement.getOperation(), statement.getTable(), statement.getFullStatement());
    return new CouchbaseOnSubscribe<>(
        originalObservable, spanName, bucket, statement.getFullStatement());
  }

  private CouchbaseOnSubscribe(
      Observable<T> originalObservable, String spanName, String bucket, String query) {
    super(originalObservable, spanName, tracer(), CLIENT);

    this.bucket = bucket;
    this.query = query;
  }

  @Override
  protected void decorateSpan(Span span) {
    span.setAttribute(SemanticAttributes.DB_SYSTEM, DbSystemValues.COUCHBASE);
    span.setAttribute(SemanticAttributes.DB_NAME, bucket);
    span.setAttribute(SemanticAttributes.DB_STATEMENT, query);
  }
}
