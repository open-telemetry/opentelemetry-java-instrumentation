/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

abstract class AbstractSqsRequest {

  public abstract ExecutionAttributes getRequest();
}
