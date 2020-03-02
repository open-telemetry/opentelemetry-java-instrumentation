package com.datadog.profiling.uploader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
enum CompressionType {
  /** No compression */
  OFF,
  /** Default compression */
  ON,
  /** Lower compression ratio with less CPU overhead * */
  LZ4,
  /** Better compression ratio for the price of higher CPU usage * */
  GZIP;

  static CompressionType of(String type) {
    if (type == null) {
      type = "";
    }

    switch (type.toLowerCase()) {
      case "off":
        return OFF;
      case "on":
        return ON;
      case "lz4":
        return LZ4;
      case "gzip":
        return GZIP;
      default:
        log.warn("Unrecognizable compression type: {}. Defaulting to 'on'.", type);
        return ON;
    }
  }
}
