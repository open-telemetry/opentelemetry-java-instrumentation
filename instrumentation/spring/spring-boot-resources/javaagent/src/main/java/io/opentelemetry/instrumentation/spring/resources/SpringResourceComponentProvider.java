/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;

// In 3.0, switch the AutoService annotation to
// io.opentelemetry.javaagent.instrumentation.spring.boot.resources.
@Deprecated // to be removed in 3.0
@AutoService(ComponentProvider.class)
public class SpringResourceComponentProvider
    extends io.opentelemetry.javaagent.instrumentation.spring.boot.resources
        .SpringResourceComponentProvider {}
