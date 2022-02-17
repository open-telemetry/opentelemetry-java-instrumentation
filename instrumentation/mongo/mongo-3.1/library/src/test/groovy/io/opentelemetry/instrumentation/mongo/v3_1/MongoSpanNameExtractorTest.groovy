/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1

import com.mongodb.event.CommandStartedEvent
import org.bson.BsonDocument
import org.bson.BsonInt32
import spock.lang.Specification

import static io.opentelemetry.instrumentation.mongo.v3_1.MongoTracingBuilder.DEFAULT_MAX_NORMALIZED_QUERY_LENGTH

class MongoSpanNameExtractorTest extends Specification {

  def 'test span name with no dbName'() {
    setup:
    def nameExtractor = new MongoSpanNameExtractor(new MongoDbAttributesExtractor(DEFAULT_MAX_NORMALIZED_QUERY_LENGTH), new MongoAttributesExtractor())
    def event = new CommandStartedEvent(
      0, null, null, command, new BsonDocument(command, new BsonInt32(1)))

    when:
    def spanName = nameExtractor.extract(event)

    then:
    spanName == command

    where:
    command = "listDatabases"
  }

}
