package datadog.opentracing.jfr.openjdk;

import datadog.opentracing.DDSpanContext;
import datadog.opentracing.jfr.DDNoopScopeEvent;
import datadog.opentracing.jfr.DDScopeEvent;
import datadog.opentracing.jfr.DDScopeEventFactory;
import jdk.jfr.EventType;

/** Event factory for {@link ScopeEvent} */
public class ScopeEventFactory implements DDScopeEventFactory {

  private final EventType eventType;

  public ScopeEventFactory() throws ClassNotFoundException {
    BlackList.checkBlackList();
    // Note: Loading ScopeEvent when ScopeEventFactory is loaded is important because it also loads
    // JFR classes - which may not be present on some JVMs
    eventType = EventType.getEventType(ScopeEvent.class);
  }

  @Override
  public DDScopeEvent create(final DDSpanContext context) {
    return eventType.isEnabled() ? new ScopeEvent(context) : DDNoopScopeEvent.INSTANCE;
  }
}
