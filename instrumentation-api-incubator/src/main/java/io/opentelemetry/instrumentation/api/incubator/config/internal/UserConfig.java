/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;

/**
 * Configuration that controls capturing the {@code enduser.*} or {@code user.*} semantic
 * attributes.
 *
 * <p>When the v3 preview is enabled, this configuration reads the corresponding {@code user.*}
 * properties instead. Legacy {@code enduser.*} properties and their associated attributes are not
 * supported when v3 preview is enabled.
 *
 * <p>The {@code enduser.*} semantic attributes are not captured by default, due to this text in the
 * specification:
 *
 * <blockquote>
 *
 * <p>Given the sensitive nature of this information, SDKs and exporters SHOULD drop these
 * attributes by default and then provide a configuration parameter to turn on retention for use
 * cases where the information is required and would not violate any policies or regulations.
 *
 * </blockquote>
 *
 * <p>Capturing of the {@code enduser.*} semantic attributes can be individually enabled by
 * configuring the following properties:
 *
 * <pre>
 * otel.instrumentation.common.enduser.id.enabled=true
 * otel.instrumentation.common.enduser.role.enabled=true
 * otel.instrumentation.common.enduser.scope.enabled=true
 * </pre>
 *
 * <p>When v3 preview is enabled, capturing of the {@code user.*} semantic attributes can be
 * individually enabled by configuring the following properties:
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
  private final boolean scopeEnabled;

  UserConfig(DeclarativeConfigProperties commonConfig, boolean v3Preview) {
    requireNonNull(commonConfig, "commonConfig must not be null");

    /*
     * Capturing enduser.* attributes is disabled by default, because of this requirement in the specification:
     *
     * Given the sensitive nature of this information, SDKs and exporters SHOULD drop these attributes by default and then provide a configuration parameter to turn on retention for use cases where the information is required and would not violate any policies or regulations.
     *
     * https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-identity-attributes
     */
    if (v3Preview) {
      this.nameEnabled = commonConfig.get("user").get("name").getBoolean("enabled", false);
      this.rolesEnabled = commonConfig.get("user").get("roles").getBoolean("enabled", false);
      this.scopeEnabled = false;
    } else {
      this.nameEnabled = commonConfig.get("enduser").get("id").getBoolean("enabled", false);
      this.rolesEnabled = commonConfig.get("enduser").get("role").getBoolean("enabled", false);
      this.scopeEnabled = commonConfig.get("enduser").get("scope").getBoolean("enabled", false);
    }
  }

  /**
   * Returns true if capturing of any identity semantic attribute is enabled.
   *
   * <p>This flag can be used by capturing instrumentations to bypass all identity attribute
   * capturing. In v3 preview mode, this corresponds to the {@code user.*} attributes; otherwise it
   * corresponds to the {@code enduser.*} attributes.
   */
  public boolean isAnyEnabled() {
    return this.nameEnabled || this.rolesEnabled || this.scopeEnabled;
  }

  /**
   * Returns true if capturing the id semantic attribute is enabled.
   *
   * <p>In v3 preview mode, this controls the {@code user.name} attribute; otherwise it controls the
   * {@code enduser.id} attribute.
   */
  public boolean isNameEnabled() {
    return this.nameEnabled;
  }

  /**
   * Returns true if capturing the role(s) semantic attribute is enabled.
   *
   * <p>In v3 preview mode, this controls the {@code user.roles} attribute; otherwise it controls
   * the {@code enduser.role} attribute.
   */
  public boolean isRolesEnabled() {
    return this.rolesEnabled;
  }

  /**
   * Returns true if capturing the {@code enduser.scope} semantic attribute is enabled.
   *
   * <p>This is always disabled in v3 preview mode, since {@code enduser.scope} has no {@code
   * user.*} equivalent.
   */
  public boolean isScopeEnabled() {
    return this.scopeEnabled;
  }
}
