package com.datadoghq.trace.agent.integration;

import com.datadoghq.trace.DDTags;
import com.mongodb.MongoClientOptions;
import com.mongodb.event.CommandStartedEvent;
import io.opentracing.Span;
import io.opentracing.contrib.mongo.TracingCommandListener;
import io.opentracing.tag.Tags;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.jboss.byteman.rule.Rule;

/** Patch the Mongo builder before constructing the final client */
@Slf4j
public class MongoHelper extends DDAgentTracingHelper<MongoClientOptions.Builder> {

  private static final List<String> WHILDCARD_FIELDS =
      Arrays.asList("ordered", "insert", "count", "find");
  private static final BsonValue HIDDEN_CAR = new BsonString("?");

  public MongoHelper(final Rule rule) {
    super(rule);
  }

  /**
   * Strategy: Just before com.mongodb.MongoClientOptions$Builder.build() method is called, we add a
   * new command listener in charge of the tracing.
   *
   * @param builder The builder instance
   * @return The same builder instance with a new tracing command listener that will be use for the
   *     client construction
   * @throws Exception
   */
  @Override
  protected MongoClientOptions.Builder doPatch(final MongoClientOptions.Builder builder)
      throws Exception {

    final TracingCommandListener listener = new TracingCommandListener(tracer);
    builder.addCommandListener(listener);

    setState(builder, 1);

    return builder;
  }

  public void decorate(final Span span, final CommandStartedEvent event) {
    try {
      // normalize the Mongo command so that parameters are removed from the string
      final BsonDocument normalized = norm(event.getCommand());
      final String mongoCmd = normalized.toString();

      // add specific resource name and replace the `db.statement` OpenTracing
      // tag with the quantized version of the Mongo command
      span.setTag(DDTags.RESOURCE_NAME, mongoCmd);
      span.setTag(Tags.DB_STATEMENT.getKey(), mongoCmd);
    } catch (final Throwable e) {
      log.warn("Couldn't decorate the mongo query: " + e.getMessage(), e);
    }
  }

  private BsonDocument norm(final BsonDocument origin) {
    final BsonDocument normalized = new BsonDocument();
    for (final Map.Entry<String, BsonValue> entry : origin.entrySet()) {
      if (WHILDCARD_FIELDS.contains(entry.getKey())) {
        normalized.put(entry.getKey(), entry.getValue());
      } else {
        final BsonValue child = norm(entry.getValue());
        normalized.put(entry.getKey(), child);
      }
    }
    return normalized;
  }

  private BsonValue norm(final BsonArray origin) {
    final BsonArray normalized = new BsonArray();
    for (final BsonValue value : origin) {
      final BsonValue child = norm(value);
      normalized.add(child);
    }
    return normalized;
  }

  private BsonValue norm(final BsonValue origin) {

    final BsonValue normalized;
    if (origin.isDocument()) {
      normalized = norm(origin.asDocument());
    } else if (origin.isArray()) {
      normalized = norm(origin.asArray());
    } else {
      normalized = HIDDEN_CAR;
    }
    return normalized;
  }
}
