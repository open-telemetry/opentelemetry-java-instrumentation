package com.datadoghq.trace;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Service {

  private final String name;
  private final String appName;
  private final Service.AppType appType;

  public Service(final String name, final String appName, final AppType appType) {
    this.name = name;
    this.appName = appName;
    this.appType = appType;
  }

  @JsonProperty("service")
  public String getName() {
    return name;
  }

  @JsonProperty("app")
  public String getAppName() {
    return appName;
  }

  @JsonProperty("app_type")
  public AppType getAppType() {
    return appType;
  }

  public enum AppType {
    WEB("web"),
    DB("db"),
    CUSTOM("custom");

    private final String type;

    AppType(final String type) {
      this.type = type;
    }

    public String toString() {
      return type;
    }
  }
}
