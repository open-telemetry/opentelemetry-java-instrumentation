/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

/**
 * Wrapper for {@link TracingRequestHandler}. Allows for wrapping a regular lambda, not proxied
 * through API Gateway. Therefore, HTTP headers propagation is not supported.
 */
public final class TracingRequestWrapper extends TracingRequestWrapperBase<Object, Object> {}
