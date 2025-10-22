package opentlelemetry.instrumentation.iceberg.v1_6;

import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.ScanReport;

import io.opentelemetry.api.OpenTelemetry;

public class IcebergMetrics implements MetricsReporter {
    private static final String INSTRUMENTATION_NAME = "io.opentelemetry.iceberg-1.6";
  
  private final OpenTelemetry openTelemetry;

  IcebergMetrics(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void report(MetricsReport report) {
    if (report instanceof ScanReport) {

    }
  }

}
