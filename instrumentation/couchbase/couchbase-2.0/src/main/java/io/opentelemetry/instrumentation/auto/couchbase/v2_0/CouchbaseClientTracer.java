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

public class CouchbaseClientTracer extends DatabaseClientTracer<String, Method> {
  public static final CouchbaseClientTracer TRACER = new CouchbaseClientTracer();

  @Override
  protected String normalizeQuery(Method method) {
    Class<?> declaringClass = method.getDeclaringClass();
    String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    return className + "." + method.getName();
  }

  @Override
  protected String dbSystem(String o) {
    return DbSystem.COUCHBASE;
  }

  @Override
  protected String dbUser(String o) {
    return null;
  }

  @Override
  protected String dbName(String o) {
    return null;
  }

  @Override
  protected InetSocketAddress peerAddress(String o) {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    // TODO this preserves old behaviour, but is confusing
    return "io.opentelemetry.auto.rxjava-1.0";
  }
}
