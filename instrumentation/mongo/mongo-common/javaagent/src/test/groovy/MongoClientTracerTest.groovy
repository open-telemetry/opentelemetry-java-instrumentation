/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static java.util.Arrays.asList

import com.mongodb.event.CommandStartedEvent
import io.opentelemetry.javaagent.instrumentation.mongo.MongoClientTracer
import org.bson.BsonArray
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import spock.lang.Specification

class MongoClientTracerTest extends Specification {
  def 'should sanitize statements to json'() {
    setup:
    def tracer = new MongoClientTracer()

    expect:
    sanitizeStatementAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonInt32(1))) ==
      '{"cmd": "?"}'

    sanitizeStatementAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonInt32(1))
        .append("sub", new BsonDocument("a", new BsonInt32(1)))) ==
      '{"cmd": "?", "sub": {"a": "?"}}'

    sanitizeStatementAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonInt32(1))
        .append("sub", new BsonArray(asList(new BsonInt32(1))))) ==
      '{"cmd": "?", "sub": ["?"]}'
  }

  def 'should only preserve string value if it is the value of the first top-level key'() {
    setup:
    def tracer = new MongoClientTracer()

    expect:
    sanitizeStatementAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonString("c"))
        .append("f", new BsonString("c"))
        .append("sub", new BsonString("c"))) ==
      '{"cmd": "c", "f": "?", "sub": "?"}'
  }

  def 'should truncate simple command'() {
    setup:
    def tracer = new MongoClientTracer(20)

    def normalized = sanitizeStatementAcrossVersions(tracer,
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

    def normalized = sanitizeStatementAcrossVersions(tracer,
      new BsonDocument("cmd", new BsonString("c"))
        .append("f1", new BsonArray(Arrays.asList(new BsonString("c1"), new BsonString("c2"))))
        .append("f2", new BsonString("c3")))
    expect:
    // this can vary because of different whitespace for different mongo versions
    normalized == '{"cmd": "c", "f1": ["?", "?' || normalized == '{"cmd": "c", "f1": ["?",'
  }

  def 'test span name with no dbName'() {
    setup:
    def tracer = new MongoClientTracer()
    def event = new CommandStartedEvent(
      0, null, null, command, new BsonDocument(command, new BsonInt32(1)))

    when:
    def spanName = tracer.spanName(event, null, null)

    then:
    spanName == command

    where:
    command = "listDatabases"
  }

  def sanitizeStatementAcrossVersions(MongoClientTracer tracer, BsonDocument query) {
    return sanitizeAcrossVersions(tracer.sanitizeStatement(query))
  }

  def sanitizeAcrossVersions(String json) {
    json = json.replaceAll('\\{ ', '{')
    json = json.replaceAll(' }', '}')
    json = json.replaceAll(' :', ':')
    return json
  }
}
