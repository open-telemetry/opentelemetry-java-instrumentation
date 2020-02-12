package com.datadog.profiling.uploader;

enum CompressionType {
  /** No compression */
  OFF,
  /** Default compression */
  ON,
  /** Lower compression ratio with less CPU overhead * */
  LOW,
  /** Better compression ratio for the price of higher CPU usage * */
  MEDIUM,
  /** Unknown compression config value */
  UNKNOWN;

  static CompressionType of(final String type) {
    if (type == null) {
      return UNKNOWN;
    }

    switch (type.toLowerCase()) {
      case "off":
        return OFF;
      case "on":
        return ON;
      case "low":
        return LOW;
      case "medium":
        return MEDIUM;
      default:
        return UNKNOWN;
    }
  }
}
