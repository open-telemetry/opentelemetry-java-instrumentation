/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo;

import static java.util.Arrays.asList;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.db.DbSystem;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

public class MongoClientTracer extends DatabaseClientTracer<CommandStartedEvent, BsonDocument> {
  private static final MongoClientTracer TRACER = new MongoClientTracer();

  public static MongoClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.mongo";
  }

  @Override
  protected String dbSystem(CommandStartedEvent event) {
    return DbSystem.MONGODB;
  }

  @Override
  protected Span onConnection(Span span, CommandStartedEvent event) {
    span.setAttribute(SemanticAttributes.DB_OPERATION, event.getCommandName());
    String collection = collectionName(event);
    if (collection != null) {
      span.setAttribute(SemanticAttributes.DB_MONGODB_COLLECTION, collection);
    }
    return super.onConnection(span, event);
  }

  @Override
  protected String dbName(CommandStartedEvent event) {
    return event.getDatabaseName();
  }

  @Override
  protected InetSocketAddress peerAddress(CommandStartedEvent event) {
    if (event.getConnectionDescription() != null
        && event.getConnectionDescription().getServerAddress() != null) {
      return event.getConnectionDescription().getServerAddress().getSocketAddress();
    } else {
      return null;
    }
  }

  @Override
  protected String dbConnectionString(CommandStartedEvent event) {
    ConnectionDescription connectionDescription = event.getConnectionDescription();
    if (connectionDescription != null) {
      ServerAddress sa = connectionDescription.getServerAddress();
      if (sa != null) {
        // https://docs.mongodb.com/manual/reference/connection-string/
        String host = sa.getHost();
        int port = sa.getPort();
        if (host != null && port != 0) {
          return "mongodb://" + host + ":" + port;
        }
      }
    }
    return null;
  }

  @Override
  public String normalizeQuery(BsonDocument statement) {
    // scrub the Mongo command so that parameters are removed from the string
    BsonDocument scrubbed = scrub(statement);
    return scrubbed.toString();
  }

  /**
   * The values of these mongo fields will not be scrubbed out. This allows the non-sensitive
   * collection names to be captured.
   */
  private static final List<String> UNSCRUBBED_FIELDS =
      asList("ordered", "insert", "count", "find", "create");

  private static final BsonValue HIDDEN_CHAR = new BsonString("?");

  private static BsonDocument scrub(BsonDocument origin) {
    BsonDocument scrub = new BsonDocument();
    for (Map.Entry<String, BsonValue> entry : origin.entrySet()) {
      if (UNSCRUBBED_FIELDS.contains(entry.getKey()) && entry.getValue().isString()) {
        scrub.put(entry.getKey(), entry.getValue());
      } else {
        BsonValue child = scrub(entry.getValue());
        scrub.put(entry.getKey(), child);
      }
    }
    return scrub;
  }

  private static BsonValue scrub(BsonArray origin) {
    BsonArray scrub = new BsonArray();
    for (BsonValue value : origin) {
      BsonValue child = scrub(value);
      scrub.add(child);
    }
    return scrub;
  }

  private static BsonValue scrub(BsonValue origin) {
    BsonValue scrubbed;
    if (origin.isDocument()) {
      scrubbed = scrub(origin.asDocument());
    } else if (origin.isArray()) {
      scrubbed = scrub(origin.asArray());
    } else {
      scrubbed = HIDDEN_CHAR;
    }
    return scrubbed;
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

  private static String collectionName(CommandStartedEvent event) {
    if (event.getCommandName().equals("getMore")) {
      if (event.getCommand().containsKey("collection")) {
        BsonValue collectionValue = event.getCommand().get("collection");
        if (collectionValue.isString()) {
          return event.getCommand().getString("collection").getValue();
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
}
