/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory.DEFAULT_MAX_NORMALIZED_QUERY_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

class MongoAttributesExtractorTest {

  private static final AttributeKey<String> DB_MONGODB_COLLECTION =
      AttributeKey.stringKey("db.mongodb.collection");

  @Test
  void shouldExtractOldCollectionAttribute() {
    MongoDbAttributesGetter getter =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);
    MongoAttributesExtractor underTest = new MongoAttributesExtractor(getter);
    CommandStartedEvent event =
        new CommandStartedEvent(
            0, null, null, "find", new BsonDocument("find", new BsonString("users")));

    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Context.root(), event);

    if (emitOldDatabaseSemconv()) {
      assertThat(attributes.build().asMap()).containsOnly(entry(DB_MONGODB_COLLECTION, "users"));
    } else {
      assertThat(attributes.build().asMap()).isEmpty();
    }
  }
}
