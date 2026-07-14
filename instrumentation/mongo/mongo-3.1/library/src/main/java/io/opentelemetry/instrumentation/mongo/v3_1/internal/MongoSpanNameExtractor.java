/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

class MongoSpanNameExtractor implements SpanNameExtractor<CommandStartedEvent> {
  private static final String DEFAULT_SPAN_NAME = "DB Query";

  private final MongoDbAttributesGetter dbAttributesGetter;
  private final SpanNameExtractor<CommandStartedEvent> stableDelegate;

  MongoSpanNameExtractor(MongoDbAttributesGetter dbAttributesGetter) {
    this.dbAttributesGetter = dbAttributesGetter;
    stableDelegate = DbClientSpanNameExtractor.create(dbAttributesGetter);
  }

  @SuppressWarnings("deprecation") // getDbName/getDbOperation are used for old semconv span names
  @Override
  public String extract(CommandStartedEvent event) {
    if (emitStableDatabaseSemconv()) {
      return stableDelegate.extract(event);
    }

    String operation = dbAttributesGetter.getDbOperation(event);
    String dbName = dbAttributesGetter.getDbName(event);
    if (operation == null) {
      return dbName == null ? DEFAULT_SPAN_NAME : dbName;
    }

    String collectionName = dbAttributesGetter.getDbCollectionName(event);
    StringBuilder name = new StringBuilder(operation);
    if (dbName != null || collectionName != null) {
      name.append(' ');
    }
    if (dbName != null && (collectionName == null || collectionName.indexOf('.') == -1)) {
      name.append(dbName);
      if (collectionName != null) {
        name.append('.');
      }
    }
    if (collectionName != null) {
      name.append(collectionName);
    }
    return name.toString();
  }
}
