/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.url.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class UrlAttributes {

  // FIXME: remove this class and replace its usages with SemanticAttributes once schema 1.21 is
  // released

  public static final AttributeKey<String> URL_FULL = stringKey("url.full");

  public static final AttributeKey<String> URL_SCHEME = stringKey("url.scheme");

  public static final AttributeKey<String> URL_PATH = stringKey("url.path");

  public static final AttributeKey<String> URL_QUERY = stringKey("url.query");

  private UrlAttributes() {}
}
