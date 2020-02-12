package datadog.opentracing.jfr;

/** Scope event */
public interface DDScopeEvent {

  void start();

  void finish();
}
