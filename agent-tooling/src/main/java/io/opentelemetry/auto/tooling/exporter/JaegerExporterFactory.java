package io.opentelemetry.auto.tooling.exporter;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.auto.config.AgentConfig;
import io.opentelemetry.auto.config.ConfigProvider;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class JaegerExporterFactory implements SpanExporterFactory {

  private static final String HOST_CONFIG = "ota.jaeger.host";

  private static final String PORT_CONFIG = "ota.jaeger.port";

  private static final String SERVICE = "ota.jaeger.service";

  @Override
  public SpanExporter newExporter() throws ExporterConfigException {
    final ConfigProvider config = AgentConfig.getDefault();
    final String host = config.get(HOST_CONFIG);
    if (host == null) {
      throw new ExporterConfigException(HOST_CONFIG + " must be specified");
    }
    final String ipStr = config.get(PORT_CONFIG);
    if (host == null) {
      throw new ExporterConfigException(PORT_CONFIG + " must be specified");
    }
    String service = config.get(SERVICE);
    if (service == null) {
      service = "(unknown)";
    }
    try {
      final int port = Integer.parseInt(ipStr);
      final ManagedChannel jaegerChannel =
          ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
      return JaegerGrpcSpanExporter.newBuilder()
          .setServiceName(service)
          .setChannel(jaegerChannel)
          .setDeadline(30000)
          .build();
    } catch (final NumberFormatException e) {
      throw new ExporterConfigException("Error parsing " + PORT_CONFIG, e);
    }
  }
}
