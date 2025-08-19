/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

// This file is generated using weaver. Do not edit manually.

/** Attribute definitions generated from a Weaver model. Do not edit manually. */
public final class CustomAttributes {

  /** The name of the network device. */
  public static final AttributeKey<String> DEVICE = stringKey("device");

  /** The direction of the flow of data being measured. */
  public static final AttributeKey<String> DIRECTION = stringKey("direction");

  /** The type of memory being measured. */
  public static final AttributeKey<String> STATE = stringKey("state");

  /** The type of measurement being taken. */
  public static final AttributeKey<String> TYPE = stringKey("type");

  private CustomAttributes() {}
}
