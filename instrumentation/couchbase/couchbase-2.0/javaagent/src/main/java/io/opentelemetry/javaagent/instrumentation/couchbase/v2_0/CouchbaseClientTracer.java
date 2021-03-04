/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;

public class CouchbaseClientTracer extends DatabaseClientTracer<Void, Method, Void> {
  private static final CouchbaseClientTracer TRACER = new CouchbaseClientTracer();

  public static CouchbaseClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String spanName(Void connection, Method method, Void sanitizedStatement) {
    Class<?> declaringClass = method.getDeclaringClass();
    String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    return className + "." + method.getName();
  }

  @Override
  protected Void sanitizeStatement(Method method) {
    return null;
  }

  @Override
  protected String dbSystem(Void connection) {
    return DbSystemValues.COUCHBASE;
  }

  @Override
  protected InetSocketAddress peerAddress(Void connection) {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.couchbase-2.0";
  }
}
