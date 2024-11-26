/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.bson.BsonValue;

class MongoAttributesExtractor implements AttributesExtractor<CommandStartedEvent, Void> {
  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_COLLECTION_NAME =
      AttributeKey.stringKey("db.collection.name");
  private static final AttributeKey<String> DB_MONGODB_COLLECTION =
      AttributeKey.stringKey("db.mongodb.collection");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, CommandStartedEvent event) {
    String collectionName = collectionName(event);
    if (collectionName != null) {
      if (emitStableDatabaseSemconv()) {
        attributes.put(DB_COLLECTION_NAME, collectionName);
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_MONGODB_COLLECTION, collectionName);
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      CommandStartedEvent event,
      @Nullable Void unused,
      @Nullable Throwable error) {}

  @Nullable
  String collectionName(CommandStartedEvent event) {
    if (event.getCommandName().equals("getMore")) {
      BsonValue collectionValue = event.getCommand().get("collection");
      if (collectionValue != null) {
        if (collectionValue.isString()) {
          return collectionValue.asString().getValue();
        }
      }
    } else if (COMMANDS_WITH_COLLECTION_NAME_AS_VALUE.contains(event.getCommandName())) {
      BsonValue commandValue = event.getCommand().get(event.getCommandName());
      if (commandValue != null && commandValue.isString()) {
        return commandValue.asString().getValue();
      }
    }
    return null;
  }

  private static final Set<String> COMMANDS_WITH_COLLECTION_NAME_AS_VALUE =
      new HashSet<>(
          asList(
              "aggregate",
              "count",
              "distinct",
              "mapReduce",
              "geoSearch",
              "delete",
              "find",
              "killCursors",
              "findAndModify",
              "insert",
              "update",
              "create",
              "drop",
              "createIndexes",
              "listIndexes"));
}
