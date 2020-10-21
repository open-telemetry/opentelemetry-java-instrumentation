/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.jdbc.DbSystem;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;

public class CouchbaseClientTracer extends DatabaseClientTracer<Void, Method> {
  public static final CouchbaseClientTracer TRACER = new CouchbaseClientTracer();

  @Override
  protected String normalizeQuery(Method method) {
    Class<?> declaringClass = method.getDeclaringClass();
    String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    return className + "." + method.getName();
  }

  @Override
  protected String dbSystem(Void connection) {
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
