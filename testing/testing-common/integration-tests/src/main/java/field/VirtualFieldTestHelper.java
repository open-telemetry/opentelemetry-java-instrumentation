/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package field;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import library.VirtualFieldTestClass;

public class VirtualFieldTestHelper {

  private static final VirtualField<VirtualFieldTestClass, String> NAME =
      VirtualField.find(VirtualFieldTestClass.class, String.class, "name");
  private static final VirtualField<VirtualFieldTestClass, String[]> STRING_ARRAY =
      VirtualField.find(VirtualFieldTestClass.class, String[].class);
  private static final VirtualField<VirtualFieldTestClass, String[][]> STRING_ARRAY_ARRAY =
      VirtualField.find(VirtualFieldTestClass.class, String[][].class);

  private VirtualFieldTestHelper() {}

  public static void test() {
    VirtualFieldTestClass instance = new VirtualFieldTestClass();
    NAME.set(instance, "test");
    NAME.get(instance);
    STRING_ARRAY.set(instance, new String[] {"test"});
    STRING_ARRAY.get(instance);
    STRING_ARRAY_ARRAY.set(instance, new String[][] {new String[] {"test"}});
    STRING_ARRAY_ARRAY.get(instance);
  }
}
