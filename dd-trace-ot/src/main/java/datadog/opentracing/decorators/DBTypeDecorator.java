package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;
import io.opentracing.tag.Tags;

/**
 * This span decorator leverages DB tags. It allows the dev to define a custom service name and
 * retrieves some DB meta such as the statement
 */
public class DBTypeDecorator extends AbstractDecorator {

  public DBTypeDecorator() {
    super();
    this.setMatchingTag(Tags.DB_TYPE.getKey());
    this.setReplacementTag(DDTags.SERVICE_NAME);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {

    // Assign service name
    if (!super.shouldSetTag(context, tag, value)) {
      // Assign span type to DB
      // Special case: Mongo, set to mongodb
      if ("mongo".equals(value)) {
        // Todo: not sure it's used cos already in the agent mongo helper
        context.setSpanType("mongodb");
      } else if ("cassandra".equals(value)) {
        context.setSpanType("cassandra");
      } else if ("memcached".equals(value)) {
        context.setSpanType("cache");
      } else {
        context.setSpanType("sql");
      }
      // Works for: mongo, cassandra, jdbc
      context.setOperationName(String.valueOf(value) + ".query");
    }
    return true;
  }
}
