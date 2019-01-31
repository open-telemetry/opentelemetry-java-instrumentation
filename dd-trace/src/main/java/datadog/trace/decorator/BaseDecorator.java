package datadog.trace.decorator;

import datadog.trace.tracer.Span;

public abstract class BaseDecorator {

  protected abstract String component();

  public Span afterStart(final Span span) {
    assert span != null;
    span.setMeta("component", component());
    return span;
  }

  public Span beforeFinish(final Span span) {
    assert span != null;
    return span;
  }

  public Span onError(final Span span, final Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      span.setErrored(true);
      span.attachThrowable(throwable);
    }
    return span;
  }
}
