/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bootstrap;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.javaagent.tooling.Constants;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BootstrapPackagesBuilderImpl implements BootstrapPackagesBuilder {

  // TODO: consider using a Trie
  private final List<String> packages = new ArrayList<>(Constants.BOOTSTRAP_PACKAGE_PREFIXES);

  @Override
  @CanIgnoreReturnValue
  public BootstrapPackagesBuilder add(String classNameOrPrefix) {
    packages.add(classNameOrPrefix);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public BootstrapPackagesBuilder addAll(Collection<String> classNamesOrPrefixes) {
    packages.addAll(classNamesOrPrefixes);
    return this;
  }

  public List<String> build() {
    return packages;
  }
}
