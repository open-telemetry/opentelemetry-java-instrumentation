/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.github.benmanes.caffeine.cache;

// Caffeine uses reflection to load cache implementations based on parameters specified by a user.
// We use gradle-shadow-plugin to minimize the dependency on Caffeine, but it does not allow
// specifying classes to keep, only artifacts. It's a relatively simple workaround for us to use
// this non-public class to create a static link to the required implementations we use.
final class CacheImplementations {

  // Each type of cache has a cache implementation and a node implementation.

  // Strong keys, strong values, maximum size
  SSMS<?, ?> ssms; // cache
  PSMS<?, ?> psms; // node

  // Weak keys, strong values, maximum size
  WSMS<?, ?> wsms; // cache
  FSMS<?, ?> fsms; // node

  // Weak keys, weak values
  WI<?, ?> wi; // cache
  FW<?, ?> fw; // node

  private CacheImplementations() {}
}
