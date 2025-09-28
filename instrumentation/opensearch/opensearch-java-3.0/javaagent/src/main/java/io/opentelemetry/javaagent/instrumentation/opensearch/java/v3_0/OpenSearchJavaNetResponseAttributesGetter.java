/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;

final class OpenSearchJavaNetResponseAttributesGetter
    implements NetworkAttributesGetter<OpenSearchJavaRequest, OpenSearchJavaResponse> {}
