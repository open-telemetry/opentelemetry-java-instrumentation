/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ConnectionId;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.jdbc.DbSystem;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

public class MongoClientTracer extends DatabaseClientTracer<CommandStartedEvent, BsonDocument> {
  public static final MongoClientTracer TRACER = new MongoClientTracer();

  // TODO use tracer names *.mongo-3.1, *.mongo-3.7, *.mongo-async-3.3 respectively in each module
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
      span.setAttribute(SemanticAttributes.MONGODB_COLLECTION, collection);
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
      Arrays.asList("ordered", "insert", "count", "find", "create");

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

  private static String collectionName(CommandStartedEvent event) {
    BsonValue collectionValue = event.getCommand().get(event.getCommandName());
    if (collectionValue != null && collectionValue.isString()) {
      return collectionValue.asString().getValue();
    }
    return null;
  }
}
