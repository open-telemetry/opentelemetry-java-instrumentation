package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;
import com.datadoghq.trace.DDTags;
import io.opentracing.tag.Tags;

public class DBStatementAsResourceName extends AbstractDecorator {

  public DBStatementAsResourceName() {
    super();
    this.setMatchingTag(Tags.DB_STATEMENT.getKey());
    this.setSetTag(DDTags.RESOURCE_NAME);
  }

  @Override
  public boolean afterSetTag(final DDSpanContext context, final String tag, final Object value) {
    //Assign service name
    if (super.afterSetTag(context, tag, value)) {
      // Replace the OT db.statement by the DD sql.query
      context.setTag(DDTags.DB_STATEMENT, value);
      return true;
    }
    return false;
  }
}
