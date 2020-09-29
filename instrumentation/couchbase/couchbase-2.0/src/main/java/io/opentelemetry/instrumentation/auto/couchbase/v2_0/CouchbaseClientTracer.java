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

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.NonNull;

public class CouchbaseClientTracer extends DatabaseClientTracer<Void, Method> {
  public static final CouchbaseClientTracer TRACER = new CouchbaseClientTracer();

  @Override
  protected @NonNull String normalizeQuery(Method method) {
    Class<?> declaringClass = method.getDeclaringClass();
    String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    return className + "." + method.getName();
  }

  @Override
  protected @NonNull String dbSystem(Void connection) {
    return DbSystem.COUCHBASE;
  }

  @Override
  protected InetSocketAddress peerAddress(Void connection) {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.couchbase";
  }
}
