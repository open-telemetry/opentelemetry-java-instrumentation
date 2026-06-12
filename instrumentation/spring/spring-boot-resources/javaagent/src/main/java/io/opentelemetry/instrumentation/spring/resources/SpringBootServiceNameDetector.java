/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;

// In 3.0, switch the AutoService annotation to
// io.opentelemetry.javaagent.instrumentation.spring.boot.resources.
@Deprecated // to be removed in 3.0
@AutoService(ResourceProvider.class)
public class SpringBootServiceNameDetector
    extends io.opentelemetry.javaagent.instrumentation.spring.boot.resources
        .SpringBootServiceNameDetector {}
