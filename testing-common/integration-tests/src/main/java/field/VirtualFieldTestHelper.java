/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package field;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import library.VirtualFieldTestClass;

public final class VirtualFieldTestHelper {

  private VirtualFieldTestHelper() {}

  public static void test() {
    VirtualFieldTestClass instance = new VirtualFieldTestClass();
    {
      VirtualField<VirtualFieldTestClass, String> field =
          VirtualField.find(VirtualFieldTestClass.class, String.class);
      field.set(instance, "test");
      field.get(instance);
    }
    {
      VirtualField<VirtualFieldTestClass, String[]> field =
          VirtualField.find(VirtualFieldTestClass.class, String[].class);
      field.set(instance, new String[] {"test"});
      field.get(instance);
    }
    {
      VirtualField<VirtualFieldTestClass, String[][]> field =
          VirtualField.find(VirtualFieldTestClass.class, String[][].class);
      field.set(instance, new String[][] {new String[] {"test"}});
      field.get(instance);
    }
  }
}
