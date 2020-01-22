package io.opentelemetry.auto.instrumentation.couchbase.client;

import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;

class CouchbaseClientDecorator extends DatabaseClientDecorator {
  public static final CouchbaseClientDecorator DECORATE = new CouchbaseClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"couchbase"};
  }

  @Override
  protected String service() {
    return "couchbase";
  }

  @Override
  protected String component() {
    return "couchbase-client";
  }

  @Override
  protected String spanType() {
    return SpanTypes.COUCHBASE;
  }

  @Override
  protected String dbType() {
    return "couchbase";
  }

  @Override
  protected String dbUser(final Object o) {
    return null;
  }

  @Override
  protected String dbInstance(final Object o) {
    return null;
  }
}
