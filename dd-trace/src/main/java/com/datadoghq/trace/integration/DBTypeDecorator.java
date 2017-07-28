package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;
import com.datadoghq.trace.DDTags;
import io.opentracing.tag.Tags;

/**
 * This span decorator leverages DB tags. It allows the dev to define a custom service name and
 * retrieves some DB meta such as the statement
 */
public class DBTypeDecorator extends AbstractDecorator {

  public DBTypeDecorator() {
    super();
    this.setMatchingTag(Tags.DB_TYPE.getKey());
    this.setSetTag(DDTags.SERVICE_NAME);
  }

  @Override
  public boolean afterSetTag(final DDSpanContext context, final String tag, final Object value) {
    //Assign service name
    if (super.afterSetTag(context, tag, value)) {
      //Assign span type to DB
      context.setSpanType("db");
      // Works for: mongo, cassandra, jdbc
      context.setOperationName(String.valueOf(value) + ".query");
      return true;
    }
    return false;
  }
}
