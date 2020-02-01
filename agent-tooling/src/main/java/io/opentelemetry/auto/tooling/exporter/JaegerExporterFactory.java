package io.opentelemetry.auto.tooling.exporter;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.auto.api.Config;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class JaegerExporterFactory implements SpanExporterFactory {

  private static final String HOST_CONFIG = "jaeger.host";

  private static final String PORT_CONFIG = "jaeger.port";

  @Override
  public SpanExporter newExporter() throws ExporterConfigException {
    final String host = Config.getSettingFromEnvironment(HOST_CONFIG, null);
    if (host == null) {
      throw new ExporterConfigException(HOST_CONFIG + " must be specified");
    }
    final String ipStr = Config.getSettingFromEnvironment(PORT_CONFIG, null);
    if (ipStr == null) {
      throw new ExporterConfigException(PORT_CONFIG + " must be specified");
    }
    final String service = Config.get().getServiceName();
    final int port;
    try {
      port = Integer.parseInt(ipStr);
    } catch (final NumberFormatException e) {
      throw new ExporterConfigException("Error parsing " + PORT_CONFIG, e);
    }
    final ManagedChannel jaegerChannel =
        ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    return JaegerGrpcSpanExporter.newBuilder()
        .setServiceName(service)
        .setChannel(jaegerChannel)
        .setDeadline(30000)
        .build();
  }
}
