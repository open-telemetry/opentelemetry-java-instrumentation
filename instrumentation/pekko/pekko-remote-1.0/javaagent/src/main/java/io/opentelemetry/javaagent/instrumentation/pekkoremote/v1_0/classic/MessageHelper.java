/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic;

import javax.annotation.Nullable;
import org.apache.pekko.remote.transport.PekkoPduCodec;
import scala.Option;
import scala.Tuple2;

public final class MessageHelper {

  /** Returns the message of a decoded pdu, null when the pdu carried only an acknowledgement. */
  @Nullable
  public static PekkoPduCodec.Message messageOf(Tuple2<?, ?> decoded) {
    Object second = decoded._2();
    if (!(second instanceof Option)) {
      return null;
    }
    Option<?> message = (Option<?>) second;
    if (message.isEmpty()) {
      return null;
    }
    Object value = message.get();
    return value instanceof PekkoPduCodec.Message ? (PekkoPduCodec.Message) value : null;
  }

  private MessageHelper() {}
}
