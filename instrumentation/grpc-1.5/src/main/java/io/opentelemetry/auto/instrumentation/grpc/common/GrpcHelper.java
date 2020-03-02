package io.opentelemetry.auto.instrumentation.grpc.common;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;

public class GrpcHelper {
  public static void addServiceName(final Span span, final String methodName) {
    String serviceName =
        "(unknown)"; // Spec says it's mandatory, so populate even if we couldn't determine it.
    final int slash = methodName.indexOf('/');
    if (slash != -1) {
      final String fullServiceName = methodName.substring(0, slash);
      final int dot = fullServiceName.lastIndexOf('.');
      if (dot != -1) {
        serviceName = fullServiceName.substring(dot + 1);
      }
    }
    span.setAttribute(MoreTags.RPC_SERVICE, serviceName); // TODO: Move rpc.service to constant
  }
}
