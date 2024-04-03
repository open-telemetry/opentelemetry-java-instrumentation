/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.semconv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;

public final class ResourceAttributes {
  private ResourceAttributes() {}

  public static final String SCHEMA_URL = SchemaUrls.V1_24_0;

  public static final AttributeKey<String> HOST_ARCH = HostIncubatingAttributes.HOST_ARCH;

  public static final AttributeKey<String> HOST_ID = HostIncubatingAttributes.HOST_ID;

  public static final AttributeKey<String> HOST_NAME = HostIncubatingAttributes.HOST_NAME;




}
