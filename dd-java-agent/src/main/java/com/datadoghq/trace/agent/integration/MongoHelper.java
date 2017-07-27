package com.datadoghq.trace.agent.integration;

import com.datadoghq.trace.DDTags;
import com.mongodb.MongoClientOptions;
import com.mongodb.event.CommandStartedEvent;
import io.opentracing.Span;
import io.opentracing.contrib.mongo.TracingCommandListener;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.jboss.byteman.rule.Rule;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Patch the Mongo builder before constructing the final client */
@Slf4j
public class MongoHelper extends DDAgentTracingHelper<MongoClientOptions.Builder> {

  private static final List<String> WHILDCARD_FIELDS = Arrays.asList("ordered", "insert");
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
      final BsonDocument normalized = new BsonDocument();
      norm(event.getCommand(), normalized);
      span.setTag(DDTags.RESOURCE_NAME, normalized.toJson());
    } catch (final Throwable e) {
      log.warn("Couldn't the mongo query: " + e.getMessage(), e);
    }
  }

  private void norm(final BsonDocument origin, final BsonDocument normalized) {
    for (final Map.Entry<String, BsonValue> entry : origin.entrySet()) {
      if (WHILDCARD_FIELDS.contains(entry.getKey())) {
        normalized.put(entry.getKey(), entry.getValue());
      } else if (entry.getValue().isDocument()) {
        final BsonDocument child = new BsonDocument();
        normalized.put(entry.getKey(), child);
        norm(entry.getValue().asDocument(), child);
      } else {
        normalized.put(entry.getKey(), HIDDEN_CAR);
      }
    }
  }
}
