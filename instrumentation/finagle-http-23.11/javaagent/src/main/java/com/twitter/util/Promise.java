package com.twitter.util;

// public accessible stubs to ensure compilation;
// these must be stripped out at jar construction
public class Promise {
  private Promise() {}

  @SuppressWarnings("ClassNamedLikeTypeParameter")
  public abstract static class K {
  }

  public abstract static class Interruptible {
  }
}
