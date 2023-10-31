/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

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
 * <p>Capturing of the {@code enduser.*} semantic attributes can be enabled by configured the
 * following property:
 *
 * <pre>
 * otel.instrumentation.common.enduser.enabled=true
 * </pre>
 *
 * <p>When {@code otel.instrumentation.common.enduser.enabled == true}, then each of the {@code
 * enduser.*} attributes will be captured, unless they have been specifically disabled with one of
 * the following properties:
 *
 * <pre>
 * otel.instrumentation.common.enduser.id.enabled=false
 * otel.instrumentation.common.enduser.role.enabled=false
 * otel.instrumentation.common.enduser.scope.enabled=false
 * </pre>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class EnduserConfig {

  private final boolean enabled;
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
    this.enabled =
        instrumentationConfig.getBoolean("otel.instrumentation.common.enduser.enabled", false);

    this.idEnabled =
        this.enabled
            && instrumentationConfig.getBoolean(
                "otel.instrumentation.common.enduser.id.enabled", true);
    this.roleEnabled =
        this.enabled
            && instrumentationConfig.getBoolean(
                "otel.instrumentation.common.enduser.role.enabled", true);
    this.scopeEnabled =
        this.enabled
            && instrumentationConfig.getBoolean(
                "otel.instrumentation.common.enduser.scope.enabled", true);
  }

  /**
   * Returns true if capturing the {@code enduser.*} semantic attributes is generally enabled.
   *
   * <p>This flag is meant to control whether enduser capturing instrumentations should be applied.
   * Whereas, the attribute-specific flags ({@link #isIdEnabled()}, {@link #isRoleEnabled()}, {@link
   * #isScopeEnabled()}) are meant to be used by instrumentations to determine which specific
   * attributes to capture.
   *
   * <p>Instrumentation implementations must also check the flags for specific attributes ({@link
   * #isIdEnabled()}, {@link #isRoleEnabled()}, {@link #isScopeEnabled()}) when deciding which
   * attribtues to capture.
   *
   * @return true if capturing the {@code enduser.*} semantic attributes is generally enabled.
   */
  public boolean isEnabled() {
    return this.enabled;
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
