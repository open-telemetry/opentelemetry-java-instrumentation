package opentlelemetry.instrumentation.iceberg.v1_6;

import org.omg.PortableInterceptor.Interceptor;

import io.opentelemetry.api.OpenTelemetry;

public class IcebergTelemetry {
  public static IcebergTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static IcebergTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new IcebergTelemetryBuilder(openTelemetry);
  }

  IcebergTelemetry() {}

  public Interceptor newTracingInterceptor() {
    return null;
  }
}
