/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import javax.annotation.Nullable;

final class RpcSemconvSelection {

  @Nullable private OpenTelemetry initializedWith;
  private volatile Selection selection = Selection.forOpenTelemetry(GlobalOpenTelemetry.getOrNoop());

  synchronized void initialize(OpenTelemetry openTelemetry) {
    if (initializedWith == openTelemetry) {
      return;
    }
    if (initializedWith != null) {
      throw new IllegalStateException(
          "RPC semantic convention component is already initialized with a different"
              + " OpenTelemetry instance");
    }
    selection = Selection.forOpenTelemetry(openTelemetry);
    initializedWith = openTelemetry;
  }

  Selection get() {
    return selection;
  }

  static final class Selection {
    private final boolean emitOld;
    private final boolean emitPreview;

    private Selection(boolean emitOld, boolean emitPreview) {
      this.emitOld = emitOld;
      this.emitPreview = emitPreview;
    }

    static Selection forOpenTelemetry(OpenTelemetry openTelemetry) {
      return new Selection(
          emitOldRpcSemconv(openTelemetry), emitPreviewRpcSemconv(openTelemetry));
    }

    boolean emitOld() {
      return emitOld;
    }

    boolean emitPreview() {
      return emitPreview;
    }
  }
}
