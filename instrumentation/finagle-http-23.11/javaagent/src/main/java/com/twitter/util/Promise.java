package com.twitter.util;

// public accessible stubs of mirrored types in com.twitter.util to ensure compilation;
// these must be stripped out at jar construction -- see build.gradle.kts
public class Promise {
  private Promise() {}

  @SuppressWarnings("ClassNamedLikeTypeParameter")
  public abstract static class K {
  }

  public abstract static class Interruptible {
  }
}
