package com.datadog.profiling.uploader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
enum CompressionType {
  /** No compression */
  OFF,
  /** Default compression */
  ON,
  /** Lower compression ratio with less CPU overhead * */
  LOW,
  /** Better compression ratio for the price of higher CPU usage * */
  MEDIUM;

  static CompressionType of(String type) {
    if (type == null) {
      type = "";
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
        log.warn("Unrecognizable compression type: {}. Defaulting to 'on'.", type);
        return ON;
    }
  }
}
