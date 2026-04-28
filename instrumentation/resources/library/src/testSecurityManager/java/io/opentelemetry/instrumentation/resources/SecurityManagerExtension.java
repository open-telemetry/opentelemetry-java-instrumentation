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

    private static final Set<String> blockedProperties = new HashSet<>();

    static {
      blockedProperties.add("java.home");
      blockedProperties.add("java.runtime.home");
      blockedProperties.add("java.runtime.version");
      blockedProperties.add("java.vm.name");
      blockedProperties.add("java.vm.vendor");
      blockedProperties.add("java.vm.version");
      blockedProperties.add("os.arch");
      blockedProperties.add("os.name");
      blockedProperties.add("os.version");
    }

    @Override
    public void checkPermission(Permission perm) {
      if (perm instanceof PropertyPermission) {
        if (blockedProperties.contains(perm.getName())) {
          throw new SecurityException("Property access not allowed.");
        }
      }
    }
  }
}
