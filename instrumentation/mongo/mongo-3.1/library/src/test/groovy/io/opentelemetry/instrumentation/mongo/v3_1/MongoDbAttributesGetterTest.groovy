/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1

import org.bson.BsonArray
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import spock.lang.Specification

import static io.opentelemetry.instrumentation.mongo.v3_1.MongoTracingBuilder.DEFAULT_MAX_NORMALIZED_QUERY_LENGTH
import static java.util.Arrays.asList

class MongoDbAttributesGetterTest extends Specification {

  def 'should sanitize statements to json'() {
    setup:
    def extractor = new MongoDbAttributesGetter(DEFAULT_MAX_NORMALIZED_QUERY_LENGTH)

    expect:
    sanitizeStatementAcrossVersions(extractor,
      new BsonDocument("cmd", new BsonInt32(1))) ==
      '{"cmd": "?"}'

    sanitizeStatementAcrossVersions(extractor,
      new BsonDocument("cmd", new BsonInt32(1))
        .append("sub", new BsonDocument("a", new BsonInt32(1)))) ==
      '{"cmd": "?", "sub": {"a": "?"}}'

    sanitizeStatementAcrossVersions(extractor,
      new BsonDocument("cmd", new BsonInt32(1))
        .append("sub", new BsonArray(asList(new BsonInt32(1))))) ==
      '{"cmd": "?", "sub": ["?"]}'
  }

  def 'should only preserve string value if it is the value of the first top-level key'() {
    setup:
    def extractor = new MongoDbAttributesGetter(DEFAULT_MAX_NORMALIZED_QUERY_LENGTH)

    expect:
    sanitizeStatementAcrossVersions(extractor,
      new BsonDocument("cmd", new BsonString("c"))
        .append("f", new BsonString("c"))
        .append("sub", new BsonString("c"))) ==
      '{"cmd": "c", "f": "?", "sub": "?"}'
  }

  def 'should truncate simple command'() {
    setup:
    def extractor = new MongoDbAttributesGetter(20)

    def normalized = sanitizeStatementAcrossVersions(extractor,
      new BsonDocument("cmd", new BsonString("c"))
        .append("f1", new BsonString("c1"))
        .append("f2", new BsonString("c2")))
    expect:
    // this can vary because of different whitespace for different mongo versions
    normalized == '{"cmd": "c", "f1": "' || normalized == '{"cmd": "c", "f1" '
  }

  def 'should truncate array'() {
    setup:
    def extractor = new MongoDbAttributesGetter(27)

    def normalized = sanitizeStatementAcrossVersions(extractor,
      new BsonDocument("cmd", new BsonString("c"))
        .append("f1", new BsonArray(asList(new BsonString("c1"), new BsonString("c2"))))
        .append("f2", new BsonString("c3")))
    expect:
    // this can vary because of different whitespace for different mongo versions
    normalized == '{"cmd": "c", "f1": ["?", "?' || normalized == '{"cmd": "c", "f1": ["?",'
  }

  def sanitizeStatementAcrossVersions(MongoDbAttributesGetter extractor, BsonDocument query) {
    return sanitizeAcrossVersions(extractor.sanitizeStatement(query))
  }

  def sanitizeAcrossVersions(String json) {
    json = json.replaceAll('\\{ ', '{')
    json = json.replaceAll(' }', '}')
    json = json.replaceAll(' :', ':')
    return json
  }

}
