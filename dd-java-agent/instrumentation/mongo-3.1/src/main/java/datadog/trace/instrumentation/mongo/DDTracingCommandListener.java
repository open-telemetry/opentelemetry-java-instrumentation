package datadog.trace.instrumentation.mongo;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import java.net.Inet4Address;
import java.net.InetAddress;
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

@Slf4j
public class DDTracingCommandListener implements CommandListener {
  /**
   * The values of these mongo fields will not be scrubbed out. This allows the non-sensitive
   * collection names to be captured.
   */
  private static final List<String> UNSCRUBBED_FIELDS =
      Arrays.asList("ordered", "insert", "count", "find", "create");

  private static final BsonValue HIDDEN_CHAR = new BsonString("?");

  private static final String MONGO_OPERATION = "mongo.query";
  private static final String COMPONENT_NAME = "java-mongo";

  private final Tracer tracer;
  /** requestID -> span */
  private final Map<Integer, Span> cache = new ConcurrentHashMap<>();

  public DDTracingCommandListener(final Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public void commandStarted(final CommandStartedEvent event) {
    final Span span = buildSpan(event);
    cache.put(event.getRequestId(), span);
  }

  @Override
  public void commandSucceeded(final CommandSucceededEvent event) {
    final Span span = cache.remove(event.getRequestId());
    if (span != null) {
      span.finish();
    }
  }

  @Override
  public void commandFailed(final CommandFailedEvent event) {
    final Span span = cache.remove(event.getRequestId());
    if (span != null) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap(ERROR_OBJECT, event.getThrowable()));
      span.finish();
    }
  }

  private Span buildSpan(final CommandStartedEvent event) {
    final Tracer.SpanBuilder spanBuilder =
        tracer.buildSpan(MONGO_OPERATION).withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

    final Span span = spanBuilder.start();
    try {
      decorate(span, event);
    } catch (final Throwable e) {
      log.warn("Couldn't decorate the mongo query: " + e.getMessage(), e);
    }

    return span;
  }

  public static void decorate(final Span span, final CommandStartedEvent event) {
    // scrub the Mongo command so that parameters are removed from the string
    final BsonDocument scrubbed = scrub(event.getCommand());
    final String mongoCmd = scrubbed.toString();

    Tags.COMPONENT.set(span, COMPONENT_NAME);
    Tags.DB_STATEMENT.set(span, mongoCmd);
    Tags.DB_INSTANCE.set(span, event.getDatabaseName());

    Tags.PEER_HOSTNAME.set(span, event.getConnectionDescription().getServerAddress().getHost());

    final InetAddress inetAddress =
        event.getConnectionDescription().getServerAddress().getSocketAddress().getAddress();
    if (inetAddress instanceof Inet4Address) {
      Tags.PEER_HOST_IPV4.set(span, inetAddress.getHostAddress());
    } else {
      Tags.PEER_HOST_IPV6.set(span, inetAddress.getHostAddress());
    }

    Tags.PEER_PORT.set(span, event.getConnectionDescription().getServerAddress().getPort());
    Tags.DB_TYPE.set(span, "mongo");

    // dd-specific tags
    span.setTag(DDTags.RESOURCE_NAME, mongoCmd);
    span.setTag(DDTags.SPAN_TYPE, "mongodb");
    span.setTag(DDTags.SERVICE_NAME, "mongo");
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
