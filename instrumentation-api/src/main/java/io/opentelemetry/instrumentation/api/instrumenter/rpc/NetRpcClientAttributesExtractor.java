/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/issues/2214">Network
 * attributes</a>. It is keep up with {@link NetClientAttributesExtractor}
 */
public abstract class NetRpcClientAttributesExtractor<REQUEST, RESPONSE>
    extends NetClientAttributesExtractor<REQUEST, RESPONSE> {}
