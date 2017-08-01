package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDTags;
import io.opentracing.tag.Tags;

/** This span decorator protect against spam on the resource name */
public class Status404Decorator extends AbstractDecorator {

  public Status404Decorator() {
    super();
    this.setMatchingTag(Tags.HTTP_STATUS.getKey());
    this.setMatchingValue(404);
    this.setSetTag(DDTags.RESOURCE_NAME);
    this.setSetValue("404");
  }
}
