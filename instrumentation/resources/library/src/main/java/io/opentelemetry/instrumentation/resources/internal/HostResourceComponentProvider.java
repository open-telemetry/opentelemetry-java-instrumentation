/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.resources.HostResource;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;

/**
 * Declarative config host resource provider.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("rawtypes")
@AutoService(ComponentProvider.class)
public class HostResourceComponentProvider extends ResourceComponentProvider {
  public HostResourceComponentProvider() {
    super(HostResource::get);
  }
}
