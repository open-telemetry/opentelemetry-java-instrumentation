/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.muzzle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ReferenceMergeUtil {

  static <T> Set<T> mergeSet(Set<T> set1, Set<T> set2) {
    Set<T> set = new LinkedHashSet<>();
    set.addAll(set1);
    set.addAll(set2);
    return set;
  }

  static Set<MethodRef> mergeMethods(Set<MethodRef> methods1, Set<MethodRef> methods2) {
    List<MethodRef> merged = new ArrayList<>(methods1);
    for (MethodRef method : methods2) {
      int i = merged.indexOf(method);
      if (i == -1) {
        merged.add(method);
      } else {
        merged.set(i, merged.get(i).merge(method));
      }
    }
    return new LinkedHashSet<>(merged);
  }

  static Set<FieldRef> mergeFields(Set<FieldRef> fields1, Set<FieldRef> fields2) {
    List<FieldRef> merged = new ArrayList<>(fields1);
    for (FieldRef field : fields2) {
      int i = merged.indexOf(field);
      if (i == -1) {
        merged.add(field);
      } else {
        merged.set(i, merged.get(i).merge(field));
      }
    }
    return new LinkedHashSet<>(merged);
  }

  static Set<Flag> mergeFlags(Set<Flag> flags1, Set<Flag> flags2) {
    Set<Flag> merged = mergeSet(flags1, flags2);
    // TODO: Assert flags are non-contradictory and resolve
    // public > protected > package-private > private
    return merged;
  }

  private ReferenceMergeUtil() {}
}
