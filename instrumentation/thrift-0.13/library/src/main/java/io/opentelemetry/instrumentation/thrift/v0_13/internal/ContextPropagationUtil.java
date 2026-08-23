/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import static java.util.Collections.emptyMap;
import static java.util.logging.Level.FINE;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TType;

final class ContextPropagationUtil {
  private static final Logger logger = Logger.getLogger(ContextPropagationUtil.class.getName());

  private static final String TRACE_CONTEXT_FIELD = "OTEL_TRACE_CONTEXT";
  // skywalking uses 8888, open tracing uses 3333
  static final short TRACE_CONTEXT_FIELD_ID = 8888;

  // using limits similar to Tomcat's maxHeaderCount and maxHttpHeaderSize
  // https://tomcat.apache.org/tomcat-9.0-doc/config/http.html
  private static final int MAX_CONTEXT_ENTRIES = 100;
  // Tomcat limits in bytes we use character count
  private static final int MAX_CONTEXT_SIZE = 8 * 1024;

  static void writeHeaders(TProtocol protocol, Map<String, String> headers) throws TException {
    protocol.writeFieldBegin(new TField(TRACE_CONTEXT_FIELD, TType.MAP, TRACE_CONTEXT_FIELD_ID));
    protocol.writeMapBegin(new TMap(TType.STRING, TType.STRING, headers.size()));

    for (Map.Entry<String, String> entry : headers.entrySet()) {
      protocol.writeString(entry.getKey());
      protocol.writeString(entry.getValue());
    }

    protocol.writeMapEnd();
    protocol.writeFieldEnd();
  }

  static Map<String, String> readHeaders(TProtocol protocol) throws TException {
    TMap map = protocol.readMapBegin();
    try {
      if (map.size > MAX_CONTEXT_ENTRIES) {
        logger.log(
            FINE,
            "Thrift context propagation entries count {0} exceeds maximum allowed of {1}, skipping context propagation.",
            new Object[] {map.size, MAX_CONTEXT_ENTRIES});
        // skip remaining entries
        for (int i = 0; i < map.size; i++) {
          TProtocolUtil.skip(protocol, TType.STRING);
          TProtocolUtil.skip(protocol, TType.STRING);
        }
        return emptyMap();
      }
      int contextSize = 0;
      Map<String, String> headers = new HashMap<>();
      for (int i = 0; i < map.size; i++) {
        String key = protocol.readString();
        String value = protocol.readString();
        contextSize += key.length() + value.length();
        if (contextSize > MAX_CONTEXT_SIZE) {
          logger.log(
              FINE,
              "Thrift context propagation payload size exceeds maximum allowed of {0}, skipping context propagation.",
              new Object[] {MAX_CONTEXT_SIZE});
          // skip remaining entries
          for (int j = i + 1; j < map.size; j++) {
            TProtocolUtil.skip(protocol, TType.STRING);
            TProtocolUtil.skip(protocol, TType.STRING);
          }
          return emptyMap();
        }
        headers.put(key, value);
      }
      return headers;
    } finally {
      protocol.readMapEnd();
    }
  }

  private ContextPropagationUtil() {}
}
