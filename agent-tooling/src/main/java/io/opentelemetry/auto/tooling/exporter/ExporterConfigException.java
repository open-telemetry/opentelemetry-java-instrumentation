package io.opentelemetry.auto.tooling.exporter;

public class ExporterConfigException extends Exception {
  public ExporterConfigException() {}

  public ExporterConfigException(final String message) {
    super(message);
  }

  public ExporterConfigException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public ExporterConfigException(final Throwable cause) {
    super(cause);
  }

  public ExporterConfigException(
      final String message,
      final Throwable cause,
      final boolean enableSuppression,
      final boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
