package io.opentelemetry.auto.instrumentation.servlet.v2_3;

import javax.servlet.http.HttpServletResponse;

public class ResponseWithStatus {
  public final HttpServletResponse response;
  public final Integer status;

  public ResponseWithStatus(HttpServletResponse response, Integer status) {
    this.response = response;
    this.status = status;
  }
}
