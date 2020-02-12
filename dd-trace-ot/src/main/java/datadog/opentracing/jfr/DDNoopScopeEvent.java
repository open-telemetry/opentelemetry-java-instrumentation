package datadog.opentracing.jfr;

/** Scope event implementation that does no reporting */
public final class DDNoopScopeEvent implements DDScopeEvent {

  public static final DDNoopScopeEvent INSTANCE = new DDNoopScopeEvent();

  @Override
  public void start() {
    // Noop
  }

  @Override
  public void finish() {
    // Noop
  }
}
