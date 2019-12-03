package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDSpanTypes;
import io.opentracing.tag.Tags;

/**
 * This span decorator leverages DB tags. It allows the dev to retrieves some DB meta such as the
 * statement
 */
public class DBTypeDecorator extends AbstractDecorator {

  public DBTypeDecorator() {
    super();
    setMatchingTag(Tags.DB_TYPE.getKey());
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {

    if ("couchbase".equals(value) || "elasticsearch".equals(value)) {
      // these instrumentation have different behavior.
      return true;
    }
    // Assign span type to DB
    // Special case: Mongo, set to mongodb
    if ("mongo".equals(value)) {
      // Todo: not sure it's used cos already in the agent mongo helper
      context.setSpanType(DDSpanTypes.MONGO);
    } else if ("cassandra".equals(value)) {
      context.setSpanType(DDSpanTypes.CASSANDRA);
    } else if ("memcached".equals(value)) {
      context.setSpanType(DDSpanTypes.MEMCACHED);
    } else {
      context.setSpanType(DDSpanTypes.SQL);
    }
    // Works for: mongo, cassandra, jdbc
    context.setOperationName(String.valueOf(value) + ".query");
    return true;
  }
}
