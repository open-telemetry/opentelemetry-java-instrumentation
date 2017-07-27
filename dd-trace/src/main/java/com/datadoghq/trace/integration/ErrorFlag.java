package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;
import io.opentracing.tag.Tags;

public class ErrorFlag extends DDSpanContextDecorator {
  public ErrorFlag() {
    super();
    this.setMatchingTag(Tags.ERROR.getKey());
  }

  @Override
  public boolean afterSetTag(DDSpanContext context, String tag, Object value) {
    //Assign resource name
    try {
      context.setErrorFlag(Boolean.parseBoolean(String.valueOf(value)));
    } catch (Throwable t) {
      //DO NOTHING
    }
    return true;
  }
}
