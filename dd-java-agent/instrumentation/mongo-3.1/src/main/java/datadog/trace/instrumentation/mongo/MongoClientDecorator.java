package datadog.trace.instrumentation.mongo;

import com.mongodb.event.CommandStartedEvent;
import datadog.trace.agent.decorator.DatabaseClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

public class MongoClientDecorator extends DatabaseClientDecorator<CommandStartedEvent> {
  public static final MongoClientDecorator DECORATE = new MongoClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"mongo"};
  }

  @Override
  protected String service() {
    return "mongo";
  }

  @Override
  protected String component() {
    return "java-mongo";
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.MONGO;
  }

  @Override
  protected String dbType() {
    return "mongo";
  }

  @Override
  protected String dbUser(final CommandStartedEvent event) {
    return null;
  }

  @Override
  protected String dbInstance(final CommandStartedEvent event) {
    return event.getDatabaseName();
    // This would be the "proper" db.instance:
    //    final ConnectionDescription connectionDescription = event.getConnectionDescription();
    //    if (connectionDescription != null) {
    //      final ConnectionId connectionId = connectionDescription.getConnectionId();
    //      if (connectionId != null) {
    //        final ServerId serverId = connectionId.getServerId();
    //        if (serverId != null) {
    //          return serverId.toString();
    //        }
    //      }
    //    }
    //    return null;
  }

  public Span onStatement(final Span span, final BsonDocument statement) {

    // scrub the Mongo command so that parameters are removed from the string
    final BsonDocument scrubbed = scrub(statement);
    final String mongoCmd = scrubbed.toString();

    span.setTag(DDTags.RESOURCE_NAME, mongoCmd);
    return onStatement(span, mongoCmd);
  }

  /**
   * The values of these mongo fields will not be scrubbed out. This allows the non-sensitive
   * collection names to be captured.
   */
  private static final List<String> UNSCRUBBED_FIELDS =
      Arrays.asList("ordered", "insert", "count", "find", "create");

  private static final BsonValue HIDDEN_CHAR = new BsonString("?");

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
