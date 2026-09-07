/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import com.datastax.oss.protocol.internal.Frame;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;

public class VirtualFieldHelper {

  public static final VirtualField<Frame, InetSocketAddress> FRAME_PEER =
      VirtualField.find(Frame.class, InetSocketAddress.class);

  private VirtualFieldHelper() {}
}
