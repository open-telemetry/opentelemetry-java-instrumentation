/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
