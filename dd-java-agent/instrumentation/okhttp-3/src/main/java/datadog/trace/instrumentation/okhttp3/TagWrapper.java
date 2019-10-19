package datadog.trace.instrumentation.okhttp3;

import datadog.trace.instrumentation.api.AgentSpan;

/**
 * Tag wrapper to store parent span context and user defined tags.
 *
 * @author Pavol Loffay
 */
public class TagWrapper {
  private AgentSpan span;

  private Object tag;

  /** @param tag user tag */
  public TagWrapper(final Object tag) {
    this.tag = tag;
  }

  /**
   * @param wrapper previous wrapper
   * @param span span
   */
  TagWrapper(final TagWrapper wrapper, final AgentSpan span) {
    this.span = span;
    tag = wrapper.tag;
  }

  public void setTag(final Object tag) {
    this.tag = tag;
  }

  public Object getTag() {
    return tag;
  }

  AgentSpan getSpan() {
    return span;
  }
}
