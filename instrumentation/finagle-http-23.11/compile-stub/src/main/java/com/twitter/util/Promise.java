/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.twitter.util;

// public accessible stubs of mirrored types in com.twitter.util to ensure compilation;
// these classes are consumed as a compileOnly module and replaced at runtime with their
// stubbed counterparts
public class Promise {
  private Promise() {}

  @SuppressWarnings("ClassNamedLikeTypeParameter")
  public abstract static class K {}

  public abstract static class Interruptible {}
}
