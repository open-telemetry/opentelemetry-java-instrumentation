/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory.DEFAULT_MAX_NORMALIZED_QUERY_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MongoSpanNameExtractorTest {

  @Test
  @DisplayName("test span name with no dbName")
  void testSpanNameWithNoDbName() {
    SpanNameExtractor<CommandStartedEvent> nameExtractor =
        DbClientSpanNameExtractor.create(
            new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH));

    String command = "listDatabases";
    CommandStartedEvent event =
        new CommandStartedEvent(
            0, null, null, command, new BsonDocument(command, new BsonInt32(1)));

    String spanName = nameExtractor.extract(event);

    assertThat(spanName).isEqualTo(command);
  }
}
