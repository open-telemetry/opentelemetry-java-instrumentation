/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.semconv.SchemaUrls;
import javax.annotation.Nullable;

/**
 * Provides RPC semantic convention selection for RPC components.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class RpcSemconvSelection {

  @Nullable private OpenTelemetry initializedWith;
  private Selection selection = Selection.forOpenTelemetry(GlobalOpenTelemetry.getOrNoop());

  public RpcSemconvSelection() {}

  public void initialize(OpenTelemetry openTelemetry) {
    // Span-name customizers may return the original extractor, and components may be reused by
    // instrumenters that share the same OpenTelemetry instance.
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

  public Selection get() {
    return selection;
  }

  /**
   * Holds the selected RPC semantic conventions.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class Selection {
    private final boolean emitOld;
    private final boolean emitPreview;

    private Selection(boolean emitOld, boolean emitPreview) {
      this.emitOld = emitOld;
      this.emitPreview = emitPreview;
    }

    // visible for testing
    public static Selection forOpenTelemetry(OpenTelemetry openTelemetry) {
      return new Selection(emitOldRpcSemconv(openTelemetry), emitPreviewRpcSemconv(openTelemetry));
    }

    public boolean emitOld() {
      return emitOld;
    }

    public boolean emitPreview() {
      return emitPreview;
    }

    public String schemaUrl() {
      return emitPreview ? RpcSchemaUrls.SCHEMA_URL : SchemaUrls.V1_37_0;
    }
  }
}
