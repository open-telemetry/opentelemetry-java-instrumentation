/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import java.util.Objects;

/**
 * Configuration that controls capturing the {@code enduser.*} semantic attributes.
 *
 * <p>The {@code enduser.*} semantic attributes are not captured by default, due to this text in the
 * specification:
 *
 * <blockquote>
 *
 * Given the sensitive nature of this information, SDKs and exporters SHOULD drop these attributes
 * by default and then provide a configuration parameter to turn on retention for use cases where
 * the information is required and would not violate any policies or regulations.
 *
 * </blockquote>
 *
 * <p>Capturing of the {@code enduser.*} semantic attributes can be individually enabled by
 * configured the following properties:
 *
 * <pre>
 * otel.instrumentation.common.enduser.id.enabled=true
 * otel.instrumentation.common.enduser.role.enabled=true
 * otel.instrumentation.common.enduser.scope.enabled=true
 * </pre>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class EnduserConfig {

  private final boolean idEnabled;
  private final boolean roleEnabled;
  private final boolean scopeEnabled;

  EnduserConfig(InstrumentationConfig instrumentationConfig) {
    Objects.requireNonNull(instrumentationConfig, "instrumentationConfig must not be null");

    /*
     * Capturing enduser.* attributes is disabled by default, because of this requirement in the specification:
     *
     * Given the sensitive nature of this information, SDKs and exporters SHOULD drop these attributes by default and then provide a configuration parameter to turn on retention for use cases where the information is required and would not violate any policies or regulations.
     *
     * https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-identity-attributes
     */
    this.idEnabled =
        instrumentationConfig.getBoolean("otel.instrumentation.common.enduser.id.enabled", false);
    this.roleEnabled =
        instrumentationConfig.getBoolean("otel.instrumentation.common.enduser.role.enabled", false);
    this.scopeEnabled =
        instrumentationConfig.getBoolean(
            "otel.instrumentation.common.enduser.scope.enabled", false);
  }

  /**
   * Returns true if capturing of any {@code enduser.*} semantic attribute is enabled.
   *
   * <p>This flag can be used by capturing instrumentations to bypass all {@code enduser.*}
   * attribute capturing.
   */
  public boolean isAnyEnabled() {
    return this.idEnabled || this.roleEnabled || this.scopeEnabled;
  }

  /**
   * Returns true if capturing the {@code enduser.id} semantic attribute is enabled.
   *
   * @return true if capturing the {@code enduser.id} semantic attribute is enabled.
   */
  public boolean isIdEnabled() {
    return this.idEnabled;
  }

  /**
   * Returns true if capturing the {@code enduser.role} semantic attribute is enabled.
   *
   * @return true if capturing the {@code enduser.role} semantic attribute is enabled.
   */
  public boolean isRoleEnabled() {
    return this.roleEnabled;
  }

  /**
   * Returns true if capturing the {@code enduser.scope} semantic attribute is enabled.
   *
   * @return true if capturing the {@code enduser.scope} semantic attribute is enabled.
   */
  public boolean isScopeEnabled() {
    return this.scopeEnabled;
  }
}
