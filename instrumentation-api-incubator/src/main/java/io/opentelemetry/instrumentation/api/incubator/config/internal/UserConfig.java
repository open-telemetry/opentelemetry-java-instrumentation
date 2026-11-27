/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;

/**
 * Configuration that controls capturing the {@code user.*} semantic attributes.
 *
 * <p>Identity attributes are not captured by default, due to this text in the specification:
 *
 * <blockquote>
 *
 * <p>Given the sensitive nature of this information, SDKs and exporters SHOULD drop these
 * attributes by default and then provide a configuration parameter to turn on retention for use
 * cases where the information is required and would not violate any policies or regulations.
 * </blockquote>
 *
 * <p>Capturing of the {@code user.*} semantic attributes can be individually enabled by configuring
 * the following properties:
 *
 * <pre>
 * otel.instrumentation.common.user.name.enabled=true
 * otel.instrumentation.common.user.roles.enabled=true
 * </pre>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class UserConfig {

  private final boolean nameEnabled;
  private final boolean rolesEnabled;

  UserConfig(DeclarativeConfigProperties commonConfig) {
    requireNonNull(commonConfig, "commonConfig must not be null");

    /*
     * Capturing user identity attributes is disabled by default, because of this requirement in the specification:
     *
     * Given the sensitive nature of this information, SDKs and exporters SHOULD drop these attributes by default and then provide a configuration parameter to turn on retention for use cases where the information is required and would not violate any policies or regulations.
     *
     * https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-identity-attributes
     */
    this.nameEnabled = commonConfig.get("user").get("name").getBoolean("enabled", false);
    this.rolesEnabled = commonConfig.get("user").get("roles").getBoolean("enabled", false);
  }

  /**
   * Returns true if capturing of any identity semantic attribute is enabled.
   *
   * <p>This flag can be used by capturing instrumentations to bypass all identity attribute
   * capturing.
   */
  public boolean isAnyEnabled() {
    return this.nameEnabled || this.rolesEnabled;
  }

  /** Returns true if capturing the {@code user.name} semantic attribute is enabled. */
  public boolean isNameEnabled() {
    return this.nameEnabled;
  }

  /** Returns true if capturing the {@code user.roles} semantic attribute is enabled. */
  public boolean isRolesEnabled() {
    return this.rolesEnabled;
  }
}
