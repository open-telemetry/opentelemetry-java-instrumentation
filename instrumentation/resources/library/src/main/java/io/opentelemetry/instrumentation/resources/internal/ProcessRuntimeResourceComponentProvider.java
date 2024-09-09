/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.resources.ProcessRuntimeResource;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;

/**
 * Declarative config process runtime resource provider.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("rawtypes")
@AutoService(ComponentProvider.class)
public class ProcessRuntimeResourceComponentProvider extends ResourceComponentProvider {
  public ProcessRuntimeResourceComponentProvider() {
    super(ProcessRuntimeResource::get);
  }
}
