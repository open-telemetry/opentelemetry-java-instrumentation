/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static java.util.Arrays.asList

import io.opentelemetry.javaagent.instrumentation.mongo.MongoClientTracer
import org.bson.BsonArray
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import spock.lang.Specification

class MongoClientTracerTest extends Specification {
  def 'should normalize queries to json'() {
    setup:
    def tracer = new MongoClientTracer()

    expect:
    normalizeQueryAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonInt32(1))) ==
      '{"cmd": "?"}'

    normalizeQueryAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonInt32(1))
        .append("sub", new BsonDocument("a", new BsonInt32(1)))) ==
      '{"cmd": "?", "sub": {"a": "?"}}'

    normalizeQueryAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonInt32(1))
        .append("sub", new BsonArray(asList(new BsonInt32(1))))) ==
      '{"cmd": "?", "sub": ["?"]}'
  }

  def 'should only preserve string value if it is the value of the first top-level key'() {
    setup:
    def tracer = new MongoClientTracer()

    expect:
    normalizeQueryAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonString("c"))
        .append("f", new BsonString("c"))
        .append("sub", new BsonString("c"))) ==
      '{"cmd": "c", "f": "?", "sub": "?"}'
  }

  def 'should truncate simple command'() {
    setup:
    def tracer = new MongoClientTracer(20)

    def normalized = normalizeQueryAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonString("c"))
        .append("f1", new BsonString("c1"))
        .append("f2", new BsonString("c2")))
    expect:
    // this can vary because of different whitespace for different mongo versions
    normalized == '{"cmd": "c", "f1": "' || normalized == '{"cmd": "c", "f1" '
  }

  def 'should truncate array'() {
    setup:
    def tracer = new MongoClientTracer(27)

    def normalized = normalizeQueryAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonString("c"))
        .append("f1", new BsonArray(Arrays.asList(new BsonString("c1"), new BsonString("c2"))))
        .append("f2", new BsonString("c3")))
    expect:
    // this can vary because of different whitespace for different mongo versions
    normalized == '{"cmd": "c", "f1": ["?", "?' || normalized == '{"cmd": "c", "f1": ["?",'
  }

  def normalizeQueryAcrossVersions(MongoClientTracer tracer, BsonDocument query) {
    return normalizeAcrossVersions(tracer.normalizeQuery(query))
  }

  def normalizeAcrossVersions(String json) {
    json = json.replaceAll('\\{ ', '{')
    json = json.replaceAll(' }', '}')
    json = json.replaceAll(' :', ':')
    return json
  }
}
