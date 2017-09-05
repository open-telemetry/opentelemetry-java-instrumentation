package com.datadoghq.trace.agent.test;

import com.google.common.collect.MapMaker;
import org.junit.Assert;
import org.junit.Test;

public class ShadowPackageRenamingTest {

  @Test
  public void test() {
    try {
      (new MapMaker()).softValues(); // this method exists in 18.0 but was removed in 20.0
    } catch (final NoSuchMethodError e) {
      Assert.fail("wrong class was loaded");
    }
  }
}
