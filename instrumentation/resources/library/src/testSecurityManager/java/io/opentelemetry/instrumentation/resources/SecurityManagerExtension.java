/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import java.security.Permission;
import java.util.HashSet;
import java.util.PropertyPermission;
import java.util.Set;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

final class SecurityManagerExtension implements BeforeEachCallback, AfterEachCallback {

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(SecurityManagerExtension.class);

  @Override
  public void beforeEach(ExtensionContext context) {
    context.getStore(NAMESPACE).put(SecurityManager.class, System.getSecurityManager());
    System.setSecurityManager(BlockPropertiesAccess.INSTANCE);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    System.setSecurityManager(
        (SecurityManager) context.getStore(NAMESPACE).get(SecurityManager.class));
  }

  private static class BlockPropertiesAccess extends SecurityManager {

    private static final BlockPropertiesAccess INSTANCE = new BlockPropertiesAccess();

    private static final Set<String> BLOCKED_PROPERTIES = new HashSet<>();

    static {
      BLOCKED_PROPERTIES.add("java.home");
      BLOCKED_PROPERTIES.add("java.runtime.home");
      BLOCKED_PROPERTIES.add("java.runtime.version");
      BLOCKED_PROPERTIES.add("java.vm.name");
      BLOCKED_PROPERTIES.add("java.vm.vendor");
      BLOCKED_PROPERTIES.add("java.vm.version");
      BLOCKED_PROPERTIES.add("os.arch");
      BLOCKED_PROPERTIES.add("os.name");
      BLOCKED_PROPERTIES.add("os.version");
    }

    @Override
    public void checkPermission(Permission perm) {
      if (perm instanceof PropertyPermission) {
        if (BLOCKED_PROPERTIES.contains(perm.getName())) {
          throw new SecurityException("Property access not allowed.");
        }
      }
    }
  }
}
