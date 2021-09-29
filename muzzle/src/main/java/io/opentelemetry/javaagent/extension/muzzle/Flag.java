/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.muzzle;

import net.bytebuddy.jar.asm.Opcodes;

/**
 * Expected flag (or lack of flag) on a class, method or field reference.
 *
 * <p>This class is used in the auto-generated {@code InstrumentationModule#getMuzzleReferences()}
 * method, it is not meant to be used directly by agent extension developers.
 */
public interface Flag {
  /**
   * Predicate method that determines whether this flag is present in the passed bitmask.
   *
   * @see Opcodes
   */
  boolean matches(int asmFlags);

  // This method is internally used to generate the InstrumentationModule#getMuzzleReferences()
  // implementation

  /** Same as {@link Enum#name()}. */
  String name();

  /**
   * The constants of this enum represent the exact visibility of a referenced class, method or
   * field.
   *
   * @see net.bytebuddy.description.modifier.Visibility
   */
  enum VisibilityFlag implements Flag {
    PUBLIC {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_PUBLIC & asmFlags) != 0;
      }
    },
    PROTECTED {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_PROTECTED & asmFlags) != 0;
      }
    },
    PACKAGE {
      @Override
      public boolean matches(int asmFlags) {
        return !(PUBLIC.matches(asmFlags)
            || PROTECTED.matches(asmFlags)
            || PRIVATE.matches(asmFlags));
      }
    },
    PRIVATE {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_PRIVATE & asmFlags) != 0;
      }
    }
  }

  /**
   * The constants of this enum represent the minimum visibility flag required by a type access,
   * method call or field access.
   *
   * @see net.bytebuddy.description.modifier.Visibility
   */
  enum MinimumVisibilityFlag implements Flag {
    PUBLIC {
      @Override
      public boolean matches(int asmFlags) {
        return VisibilityFlag.PUBLIC.matches(asmFlags);
      }
    },
    PROTECTED_OR_HIGHER {
      @Override
      public boolean matches(int asmFlags) {
        return VisibilityFlag.PUBLIC.matches(asmFlags)
            || VisibilityFlag.PROTECTED.matches(asmFlags);
      }
    },
    PACKAGE_OR_HIGHER {
      @Override
      public boolean matches(int asmFlags) {
        return !VisibilityFlag.PRIVATE.matches(asmFlags);
      }
    },
    PRIVATE_OR_HIGHER {
      @Override
      public boolean matches(int asmFlags) {
        // you can't out-private a private
        return true;
      }
    }
  }

  /**
   * The constants of this enum describe whether a method or class is abstract, final or non-final.
   *
   * @see net.bytebuddy.description.modifier.TypeManifestation
   * @see net.bytebuddy.description.modifier.MethodManifestation
   */
  enum ManifestationFlag implements Flag {
    FINAL {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_FINAL & asmFlags) != 0;
      }
    },
    NON_FINAL {
      @Override
      public boolean matches(int asmFlags) {
        return !(ABSTRACT.matches(asmFlags) || FINAL.matches(asmFlags));
      }
    },
    ABSTRACT {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_ABSTRACT & asmFlags) != 0;
      }
    },
    INTERFACE {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_INTERFACE & asmFlags) != 0;
      }
    },
    NON_INTERFACE {
      @Override
      public boolean matches(int asmFlags) {
        return !INTERFACE.matches(asmFlags);
      }
    }
  }

  /**
   * The constants of this enum describe whether a method/field is static or not.
   *
   * @see net.bytebuddy.description.modifier.Ownership
   */
  enum OwnershipFlag implements Flag {
    STATIC {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_STATIC & asmFlags) != 0;
      }
    },
    NON_STATIC {
      @Override
      public boolean matches(int asmFlags) {
        return !STATIC.matches(asmFlags);
      }
    }
  }
}
