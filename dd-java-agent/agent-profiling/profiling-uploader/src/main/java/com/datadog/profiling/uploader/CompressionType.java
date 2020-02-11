package com.datadog.profiling.uploader;

enum CompressionType {
  /** No compression */
  OFF,
  /** Default compression */
  ON,
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
      default:
        return UNKNOWN;
    }
  }
}
