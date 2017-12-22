package com.datadoghq.trace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public class Service {

  private final String name;
  private final String appName;
  private final Service.AppType appType;

  public Service(final String name, final String appName, final AppType appType) {
    this.name = name;
    this.appName = appName;
    this.appType = appType;
  }

  @JsonIgnore
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

  @Override
  public String toString() {
    return "Service { "
        + "name='"
        + name
        + "\', appName='"
        + appName
        + "', appType="
        + appType
        + " }";
  }

  public enum AppType {
    WEB("web"),
    DB("db"),
    CUSTOM("custom"),
    CACHE("cache"),
    WORKER("worker");

    private final String type;

    AppType(final String type) {
      this.type = type;
    }

    @JsonValue
    public String toString() {
      return type;
    }
  }
}
