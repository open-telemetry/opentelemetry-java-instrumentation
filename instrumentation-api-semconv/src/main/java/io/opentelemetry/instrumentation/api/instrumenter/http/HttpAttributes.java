/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

final class HttpAttributes {

  // FIXME: remove this class and replace its usages with SemanticAttributes once schema 1.21 is
  // released

  static final AttributeKey<String> HTTP_REQUEST_METHOD = stringKey("http.request.method");

  static final AttributeKey<Long> HTTP_REQUEST_BODY_SIZE = longKey("http.request.body.size");

  static final AttributeKey<Long> HTTP_RESPONSE_BODY_SIZE = longKey("http.response.body.size");

  static final AttributeKey<Long> HTTP_RESPONSE_STATUS_CODE = longKey("http.response.status_code");

  private HttpAttributes() {}
}
