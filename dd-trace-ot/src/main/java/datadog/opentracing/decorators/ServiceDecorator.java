package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;

public class ServiceDecorator extends AbstractDecorator {

  public ServiceDecorator() {
    super();
    this.setMatchingTag("service"); // will be added to a future OT version.
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    context.setServiceName(String.valueOf(value));
    return false;
  }
}
