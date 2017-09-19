package com.datadoghq.agent;

import com.datadoghq.trace.resolver.TracerConfig;
import java.util.ArrayList;
import java.util.List;

/** Configuration POJO for the agent */
public class TracingAgentConfig extends TracerConfig {

  private List<String> disabledInstrumentations = new ArrayList<>();

  private String[] enableCustomAnnotationTracingOver = {};

  public String[] getEnableCustomAnnotationTracingOver() {
    return enableCustomAnnotationTracingOver;
  }

  public void setEnableCustomAnnotationTracingOver(
      final String[] enableCustomAnnotationTracingOver) {
    this.enableCustomAnnotationTracingOver = enableCustomAnnotationTracingOver;
  }

  public List<String> getDisabledInstrumentations() {
    return disabledInstrumentations;
  }

  public void setDisabledInstrumentations(final List<String> uninstallContributions) {
    this.disabledInstrumentations = uninstallContributions;
  }
}
