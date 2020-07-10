package io.opentelemetry.instrumentation.spring.autoconfigure.helpers;

public class PropertiesBase {
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
