/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0;

import org.apache.thrift.protocol.TProtocol;

/**
 * Note that the 8888th field of record is reserved for transporting trace header. Because Thrift
 * doesn't support to transport metadata.
 */
public abstract class AbstractProtocolWrapper extends TProtocolDecorator {
  public static final String OT_MAGIC_FIELD = "OT_MAGIC_FIELD";
  public static final short OT_MAGIC_FIELD_ID = 8888;

  public AbstractProtocolWrapper(TProtocol protocol) {
    super(protocol);
  }
}
