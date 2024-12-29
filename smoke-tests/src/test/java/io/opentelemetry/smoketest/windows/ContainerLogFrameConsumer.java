/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.windows;

import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.model.Frame;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ContainerLogFrameConsumer
    extends ResultCallbackTemplate<ContainerLogFrameConsumer, Frame>
    implements ContainerLogHandler {
  private final List<Listener> listeners;

  public ContainerLogFrameConsumer() {
    this.listeners = new ArrayList<>();
  }

  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void onNext(Frame frame) {
    LineType lineType = getLineType(frame);

    if (lineType != null) {
      byte[] bytes = frame.getPayload();
      String text = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);

      for (Listener listener : listeners) {
        listener.accept(lineType, text);
      }
    }
  }

  private static LineType getLineType(Frame frame) {
    switch (frame.getStreamType()) {
      case STDOUT:
        return LineType.STDOUT;
      case STDERR:
        return LineType.STDERR;
      default:
        return null;
    }
  }
}
