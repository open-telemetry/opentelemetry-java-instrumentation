package io.opentelemetry.auto.instrumentation.servlet.v2_3;

import io.opentelemetry.auto.instrumentation.servlet.ServletHttpServerTracer;

public class Servlet2HttpServerTracer extends ServletHttpServerTracer<ResponseWithStatus> {

  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.servlet-2.3";
  }

  @Override
  protected int status(ResponseWithStatus response) {
    return response.status;
  }
}
