package opentlelemetry.instrumentation.iceberg.v1_6;


import io.opentelemetry.api.OpenTelemetry;

public class IcebergTelemetry {
  private final OpenTelemetry openTelemetry;

  public static IcebergTelemetry create(OpenTelemetry openTelemetry) {
    return new IcebergTelemetry(openTelemetry);
  }

  IcebergTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

}
