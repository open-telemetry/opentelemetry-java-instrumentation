package io.opentelemetry.javaagent.instrumentation.apiwiz_logging.model;


import java.io.Serializable;

public class ComplianceCheckDto implements Serializable {

  private Request request;
  private Response response;
  private String clientIp;
  private String clientHost;
  private String serverIp;
  private String serverHost;

  private String otelTraceId;
  private String otelSpanId;

  public Request getRequest() {
    return request;
  }

  public void setRequest(Request request) {
    this.request = request;
  }

  public Response getResponse() {
    return response;
  }

  public void setResponse(Response response) {
    this.response = response;
  }

  public String getClientIp() {
    return clientIp;
  }

  public void setClientIp(String clientIp) {
    this.clientIp = clientIp;
  }

  public String getClientHost() {
    return clientHost;
  }

  public void setClientHost(String clientHost) {
    this.clientHost = clientHost;
  }

  public String getServerIp() {
    return serverIp;
  }

  public void setServerIp(String serverIp) {
    this.serverIp = serverIp;
  }

  public String getServerHost() {
    return serverHost;
  }

  public void setServerHost(String serverHost) {
    this.serverHost = serverHost;
  }

  public String getOtelTraceId() {
    return otelTraceId;
  }

  public void setOtelTraceId(String otelTraceId) {
    this.otelTraceId = otelTraceId;
  }

  public String getOtelSpanId() {
    return otelSpanId;
  }

  public void setOtelSpanId(String otelSpanId) {
    this.otelSpanId = otelSpanId;
  }
}
