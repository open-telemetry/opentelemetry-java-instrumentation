package io.opentelemetry.javaagent.bootstrap.context.config;


import javax.annotation.Nullable;

public final class Config {
  private Config() {
  }

  @Nullable public static String traceIdKey = System.getProperty("api.compliance.tracing.traceId");

  @Nullable public static String spanIdKey = System.getProperty("api.compliance.tracing.spanId");

  @Nullable public static String parentSpanIdKey = System.getProperty(
      "api.compliance.tracing.parentSpanId");

  @Nullable public static String requestTimeStampKey = System.getProperty(
      "api.compliance.tracing.requestTimeStamp");

  @Nullable public static String responseTimeStampKey = System.getProperty(
      "api.compliance.tracing.responseTimeStamp");

  @Nullable public static String gatewayTypeKey = System.getProperty(
      "api.compliance.tracing.gatewayType");

  @Nullable public static String apiwizDetectUrl = System.getProperty("api.compliance.detect.api");

  @Nullable public static String workspaceId = System.getProperty("workspace-id");

  @Nullable public static String apiKey = System.getProperty("x-apikey");

  @Nullable public static String serverIp = System.getProperty("server-ip");
}
