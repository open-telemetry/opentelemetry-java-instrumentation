package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDTags;
import io.opentracing.tag.Tags;

public class DBStatementAsResourceName extends AbstractDecorator {

  public DBStatementAsResourceName() {
    super();
    this.setMatchingTag(Tags.DB_STATEMENT.getKey());
    this.setSetTag(DDTags.RESOURCE_NAME);
  }
}
