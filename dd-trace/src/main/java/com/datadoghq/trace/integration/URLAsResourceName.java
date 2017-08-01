package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;
import com.datadoghq.trace.DDTags;
import io.opentracing.tag.Tags;
import java.net.MalformedURLException;

/** Decorator for servlet contrib */
public class URLAsResourceName extends AbstractDecorator {

  public URLAsResourceName() {
    super();
    this.setMatchingTag(Tags.HTTP_URL.getKey());
    this.setSetTag(DDTags.RESOURCE_NAME);
  }

  @Override
  public boolean afterSetTag(final DDSpanContext context, final String tag, final Object value) {
    //Assign resource name
    if (context.getTags().containsKey(Tags.COMPONENT.getKey())
        && "java-web-servlet".equals(context.getTags().get(Tags.COMPONENT.getKey()))) {
      try {
        final String path = new java.net.URL(String.valueOf(value)).getPath();
        context.setResourceName(path);
      } catch (final MalformedURLException e) {
        context.setResourceName(String.valueOf(value));
      }
    }
    return true;
  }
}
