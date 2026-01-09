package io.opentelemetry.instrumentation.moduleconfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ModuleConfiguration {

  public static class Configuration {
    private String name;
    private ModuleData config;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public ModuleData getConfig() {
      return config;
    }

    public void setConfig(ModuleData config) {
      this.config = config;
    }
  }

  public static class ModuleData {
    private boolean enabled;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

//  @JsonProperty("default_config")
  private ModuleData defaultConfig;
  private List<Configuration> modules;

  public ModuleData getDefaultConfig() {
    return defaultConfig;
  }

  public void setDefaultConfig(ModuleData defaultConfig) {
    this.defaultConfig = defaultConfig;
  }

  public List<Configuration> getModules() {
    return modules;
  }

  public void setModules(List<Configuration> modules) {
    this.modules = modules;
  }
}
