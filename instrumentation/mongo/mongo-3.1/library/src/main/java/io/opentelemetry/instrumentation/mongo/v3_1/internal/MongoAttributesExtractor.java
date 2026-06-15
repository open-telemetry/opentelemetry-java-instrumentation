/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class MongoAttributesExtractor implements AttributesExtractor<CommandStartedEvent, Void> {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_MONGODB_COLLECTION =
      AttributeKey.stringKey("db.mongodb.collection");

  private final MongoDbAttributesGetter getter;

  MongoAttributesExtractor(MongoDbAttributesGetter getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, CommandStartedEvent event) {
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_MONGODB_COLLECTION, getter.getDbCollectionName(event));
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      CommandStartedEvent event,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
