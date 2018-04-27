package datadog.trace.instrumentation.okhttp3;

import io.opentracing.Span;

/**
 * Tag wrapper to store parent span context and user defined tags.
 *
 * @author Pavol Loffay
 */
public class TagWrapper {
  private Span span;

  private Object tag;

  /** @param tag user tag */
  public TagWrapper(final Object tag) {
    this.tag = tag;
  }

  /**
   * @param wrapper previous wrapper
   * @param span span
   */
  TagWrapper(final TagWrapper wrapper, final Span span) {
    this.span = span;
    this.tag = wrapper.tag;
  }

  public void setTag(final Object tag) {
    this.tag = tag;
  }

  public Object getTag() {
    return tag;
  }

  Span getSpan() {
    return span;
  }
}
