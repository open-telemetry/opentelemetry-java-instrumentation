package com.datadoghq.trace.agent;

import com.datadoghq.trace.resolver.TracerConfig;
import java.util.ArrayList;
import java.util.List;

/** Configuration POJO for the agent */
public class TracingAgentConfig extends TracerConfig {

  private List<String> disabledInstrumentations = new ArrayList<String>();

  private String[] enableCustomAnnotationTracingOver = {};

  public String[] getEnableCustomAnnotationTracingOver() {
    return enableCustomAnnotationTracingOver;
  }

  public void setEnableCustomAnnotationTracingOver(String[] enableCustomAnnotationTracingOver) {
    this.enableCustomAnnotationTracingOver = enableCustomAnnotationTracingOver;
  }

  public List<String> getDisabledInstrumentations() {
    return disabledInstrumentations;
  }

  public void setDisabledInstrumentations(List<String> uninstallContributions) {
    this.disabledInstrumentations = uninstallContributions;
  }
}
