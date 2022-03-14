/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

/**
 * An interface for getting RPC client attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link RpcClientAttributesExtractor} or {@link
 * RpcServerAttributesExtractor} to obtain the various RPC attributes in a type-generic way.
 */
public interface RpcClientAttributesGetter<REQUEST> extends RpcCommonAttributesGetter<REQUEST> {}
