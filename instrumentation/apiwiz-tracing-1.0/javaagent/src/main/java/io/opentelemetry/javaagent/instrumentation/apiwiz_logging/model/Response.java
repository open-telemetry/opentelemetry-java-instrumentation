package io.opentelemetry.javaagent.instrumentation.apiwiz_logging.model;

import java.io.Serializable;
import java.util.Map;

public class Response implements Serializable {

  private Map<String, Object> headerParams;
  private String responseBody;
  private String statusCode;

  public Map<String, Object> getHeaderParams() {
    return headerParams;
  }

  public void setHeaderParams(Map<String, Object> headerParams) {
    this.headerParams = headerParams;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(String statusCode) {
    this.statusCode = statusCode;
  }
}
