package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;
import com.datadoghq.trace.DDTags;
import io.opentracing.tag.Tags;

/**
 * This span decorator leverages HTTP tags. It allows the dev to define a custom service name and
 * retrieves some HTTP meta such as the request path
 */
public class HTTPComponent extends AbstractDecorator {

  public HTTPComponent() {
    super();
    this.setMatchingTag(Tags.COMPONENT.getKey());
    this.setSetTag(DDTags.SERVICE_NAME);
  }

  @Override
  public boolean afterSetTag(final DDSpanContext context, final String tag, final Object value) {
    //Assign service name
    if (super.afterSetTag(context, tag, value)) {
      //Assign span type to WEB
      context.setSpanType("web");
      return true;
    } else {
      return false;
    }
  }
}
