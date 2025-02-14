package io.opentelemetry.javaagent.instrumentation.apiwiz_logging.model;

import java.io.Serializable;
import java.util.Map;

public class Request implements Serializable {


  private Map<String, Object> headerParams;
  private Map<String, Object> queryParams;
  private Map<String, Object> formParams;
  private Map<String, Object> pathParams;
  private String verb;

  public Map<String, Object> getHeaderParams() {
    return headerParams;
  }

  public void setHeaderParams(Map<String, Object> headerParams) {
    this.headerParams = headerParams;
  }

  public Map<String, Object> getQueryParams() {
    return queryParams;
  }

  public void setQueryParams(Map<String, Object> queryParams) {
    this.queryParams = queryParams;
  }

  public Map<String, Object> getFormParams() {
    return formParams;
  }

  public void setFormParams(Map<String, Object> formParams) {
    this.formParams = formParams;
  }

  public Map<String, Object> getPathParams() {
    return pathParams;
  }

  public void setPathParams(Map<String, Object> pathParams) {
    this.pathParams = pathParams;
  }

  public String getVerb() {
    return verb;
  }

  public void setVerb(String verb) {
    this.verb = verb;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public String getRequestBody() {
    return requestBody;
  }

  public void setRequestBody(String requestBody) {
    this.requestBody = requestBody;
  }

  public String getScheme() {
    return scheme;
  }

  public void setScheme(String scheme) {
    this.scheme = scheme;
  }

  public int getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  private String path;
  private String hostname;
  private String requestBody;
  private String scheme;
  private Integer port;
}
