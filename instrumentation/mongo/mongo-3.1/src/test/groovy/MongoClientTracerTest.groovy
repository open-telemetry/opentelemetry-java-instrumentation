/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.javaagent.instrumentation.mongo.MongoClientTracer
import org.bson.BsonArray
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import spock.lang.Specification

import static java.util.Arrays.asList

class MongoClientTracerTest extends Specification {
  def 'should normalize queries to json'() {
    setup:
    def tracer = new MongoClientTracer()

    expect:
    tracer.normalizeQuery(
      new BsonDocument("cmd", new BsonInt32(1))) ==
      '{ "cmd" : "?" }'

    tracer.normalizeQuery(
      new BsonDocument("cmd", new BsonInt32(1))
        .append("sub", new BsonDocument("a", new BsonInt32(1)))) ==
      '{ "cmd" : "?", "sub" : { "a" : "?" } }'

    tracer.normalizeQuery(
      new BsonDocument("cmd", new BsonInt32(1))
        .append("sub", new BsonArray(asList(new BsonInt32(1))))) ==
      '{ "cmd" : "?", "sub" : ["?"] }'
  }

  def 'should only preserve string value if it is the value of the first top-level key'() {
    setup:
    def tracer = new MongoClientTracer()

    expect:
    tracer.normalizeQuery(
      new BsonDocument("cmd", new BsonString("c"))
      .append("f", new BsonString("c"))
      .append("sub", new BsonString("c"))) ==
      '{ "cmd" : "c", "f" : "?", "sub" : "?" }'
  }

  def 'should truncate simple command'() {
    setup:
    def tracer = new MongoClientTracer(20)

    expect:
    tracer.normalizeQuery(
      new BsonDocument("cmd", new BsonString("c"))
        .append("f1", new BsonString("c1"))
        .append("f2", new BsonString("c2"))) ==
      '{ "cmd" : "c", "f1" '
  }

  def 'should truncate array'() {
    setup:
    def tracer = new MongoClientTracer(27)

    expect:
    tracer.normalizeQuery(
      new BsonDocument("cmd", new BsonString("c"))
        .append("f1", new BsonArray(asList(new BsonString("c1"), new BsonString("c2"))))
        .append("f2", new BsonString("c3"))) ==
      '{ "cmd" : "c", "f1" : ["?",'
  }
}
