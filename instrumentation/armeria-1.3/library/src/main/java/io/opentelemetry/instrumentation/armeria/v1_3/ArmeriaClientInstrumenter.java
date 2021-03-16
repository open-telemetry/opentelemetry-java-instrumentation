package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Arrays;

final class ArmeriaClientInstrumenter extends Instrumenter<ClientRequestContext, RequestLog> {

  ArmeriaClientInstrumenter(OpenTelemetry openTelemetry) {
    super(
        openTelemetry.getTracer("io.opentelemetry.armeria-1.3"),
        Arrays.asList(ArmeriaHttpAttributesExtractor.INSTANCE, ArmeriaNetAttributesExtractor.INSTANCE));
  }

  @Override
  protected String spanName(ClientRequestContext clientRequestContext) {
    // TODO(anuraaga): Move this to a utility (not base class).
    HttpRequest request = clientRequestContext.request();
    if (request != null) {
      return "HTTP " + request.method().name();
    }
    return "HTTP request";
  }

  @Override
  protected SpanKind spanKind(
      ClientRequestContext clientRequestContext) {
    return SpanKind.CLIENT;
  }
}
