package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class OpenSearchJavaSingletons {
  private static final Instrumenter<OpenSearchJavaRequest, OpenSearchJavaResponse> INSTRUMENTER =
      OpenSearchJavaInstrumenterFactory.create("io.opentelemetry.opensearch-java-3.0");

  public static Instrumenter<OpenSearchJavaRequest, OpenSearchJavaResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private OpenSearchJavaSingletons() {}
}
