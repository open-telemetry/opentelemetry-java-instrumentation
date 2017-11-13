package com.datadoghq.agent.integration;

import com.datadoghq.trace.DDTags;
import com.mongodb.MongoClientOptions;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.jboss.byteman.rule.Rule;

/** Patch the Mongo builder before constructing the final client */
@Slf4j
public class MongoHelper extends DDAgentTracingHelper<MongoClientOptions.Builder> {

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

    final DDTracingCommandListener listener = new DDTracingCommandListener(tracer);
    builder.addCommandListener(listener);

    setState(builder, 1);

    return builder;
  }

  public static class DDTracingCommandListener implements CommandListener {
    /**
     * The values of these mongo fields will not be scrubbed out. This allows the non-sensitive
     * collection names to be captured.
     */
    private static final List<String> UNSCRUBBED_FIELDS =
        Arrays.asList("ordered", "insert", "count", "find", "create");

    private static final BsonValue HIDDEN_CHAR = new BsonString("?");
    private static final String MONGO_OPERATION = "mongo.query";

    static final String COMPONENT_NAME = "java-mongo";
    private final Tracer tracer;
    /** Cache for (request id, span) pairs */
    private final Map<Integer, Span> cache = new ConcurrentHashMap<>();

    public DDTracingCommandListener(Tracer tracer) {
      this.tracer = tracer;
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
      Span span = buildSpan(event);
      cache.put(event.getRequestId(), span);
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
      Span span = cache.remove(event.getRequestId());
      if (span != null) {
        span.finish();
      }
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
      Span span = cache.remove(event.getRequestId());
      if (span != null) {
        onError(span, event.getThrowable());
        span.finish();
      }
    }

    private Span buildSpan(CommandStartedEvent event) {
      Tracer.SpanBuilder spanBuilder =
          tracer.buildSpan(MONGO_OPERATION).withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

      Span span = spanBuilder.startManual();
      try {
        decorate(span, event);
      } catch (final Throwable e) {
        log.warn("Couldn't decorate the mongo query: " + e.getMessage(), e);
      }

      return span;
    }

    private static void onError(Span span, Throwable throwable) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap("error.object", throwable));
    }

    public static void decorate(Span span, CommandStartedEvent event) {
      // scrub the Mongo command so that parameters are removed from the string
      final BsonDocument scrubbed = scrub(event.getCommand());
      final String mongoCmd = scrubbed.toString();

      Tags.COMPONENT.set(span, COMPONENT_NAME);
      Tags.DB_STATEMENT.set(span, mongoCmd);
      Tags.DB_INSTANCE.set(span, event.getDatabaseName());
      // add specific resource name
      span.setTag(DDTags.RESOURCE_NAME, mongoCmd);
      span.setTag(DDTags.SPAN_TYPE, "mongodb");
      span.setTag(DDTags.SERVICE_NAME, "mongo");

      Tags.PEER_HOSTNAME.set(span, event.getConnectionDescription().getServerAddress().getHost());

      InetAddress inetAddress =
          event.getConnectionDescription().getServerAddress().getSocketAddress().getAddress();

      if (inetAddress instanceof Inet4Address) {
        byte[] address = inetAddress.getAddress();
        Tags.PEER_HOST_IPV4.set(span, ByteBuffer.wrap(address).getInt());
      } else {
        Tags.PEER_HOST_IPV6.set(span, inetAddress.getHostAddress());
      }

      Tags.PEER_PORT.set(span, event.getConnectionDescription().getServerAddress().getPort());
      Tags.DB_TYPE.set(span, "mongo");
    }

    private static BsonDocument scrub(final BsonDocument origin) {
      final BsonDocument scrub = new BsonDocument();
      for (final Map.Entry<String, BsonValue> entry : origin.entrySet()) {
        if (UNSCRUBBED_FIELDS.contains(entry.getKey()) && entry.getValue().isString()) {
          scrub.put(entry.getKey(), entry.getValue());
        } else {
          final BsonValue child = scrub(entry.getValue());
          scrub.put(entry.getKey(), child);
        }
      }
      return scrub;
    }

    private static BsonValue scrub(final BsonArray origin) {
      final BsonArray scrub = new BsonArray();
      for (final BsonValue value : origin) {
        final BsonValue child = scrub(value);
        scrub.add(child);
      }
      return scrub;
    }

    private static BsonValue scrub(final BsonValue origin) {
      final BsonValue scrubbed;
      if (origin.isDocument()) {
        scrubbed = scrub(origin.asDocument());
      } else if (origin.isArray()) {
        scrubbed = scrub(origin.asArray());
      } else {
        scrubbed = HIDDEN_CHAR;
      }
      return scrubbed;
    }
  }
}
