package datadog.opentracing.scopemanager;

import io.opentracing.ScopeManager;

/** Represents a ScopeManager that is only valid in certain cases such as on a specific thread. */
@Deprecated
public interface ScopeContext extends ScopeManager {

  /**
   * When multiple ScopeContexts are active, the first one to respond true will have control.
   *
   * @return true if this ScopeContext should be active
   */
  boolean inContext();
}
