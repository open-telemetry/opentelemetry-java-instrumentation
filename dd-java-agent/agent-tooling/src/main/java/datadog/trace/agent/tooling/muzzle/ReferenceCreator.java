package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

/** Visit a class and collect all references made by the visited class. */
// Additional things we could check
// - annotations on class
// - outer class
// - inner class
// - cast opcodes in method bodies
public class ReferenceCreator extends ClassVisitor {
  /**
   * Classes in this namespace will be scanned and used to create references.
   *
   * <p>For now we're hardcoding this to the instrumentation package so we only create references
   * from the method advice and helper classes.
   */
  private static final String REFERENCE_CREATION_PACKAGE = "datadog.trace.instrumentation.";

  public static Map<String, Reference> createReferencesFrom(
      final String entryPointClassName, final ClassLoader loader) {
    return ReferenceCreator.createReferencesFrom(entryPointClassName, loader, true);
  }

  /**
   * Generate all references reachable from a given class.
   *
   * @param entryPointClassName Starting point for generating references.
   * @param loader Classloader used to read class bytes.
   * @param startFromMethodBodies if true only create refs from method bodies.
   * @return Map of [referenceClassName -> Reference]
   * @throws IllegalStateException if class is not found or unable to be loaded.
   */
  private static Map<String, Reference> createReferencesFrom(
      final String entryPointClassName, final ClassLoader loader, boolean startFromMethodBodies)
      throws IllegalStateException {
    final Set<String> visitedSources = new HashSet<>();
    final Map<String, Reference> references = new HashMap<>();

    final Queue<String> instrumentationQueue = new ArrayDeque<>();
    instrumentationQueue.add(entryPointClassName);

    while (!instrumentationQueue.isEmpty()) {
      final String className = instrumentationQueue.remove();
      visitedSources.add(className);
      final InputStream in = loader.getResourceAsStream(Utils.getResourceName(className));
      try {
        final ReferenceCreator cv = new ReferenceCreator(null, startFromMethodBodies);
        // only start from method bodies on the first pass
        startFromMethodBodies = false;
        final ClassReader reader = new ClassReader(in);
        reader.accept(cv, ClassReader.SKIP_FRAMES);

        final Map<String, Reference> instrumentationReferences = cv.getReferences();
        for (final Map.Entry<String, Reference> entry : instrumentationReferences.entrySet()) {
          // Don't generate references created outside of the datadog instrumentation package.
          if (!visitedSources.contains(entry.getKey())
              && entry.getKey().startsWith(REFERENCE_CREATION_PACKAGE)) {
            instrumentationQueue.add(entry.getKey());
          }
          if (references.containsKey(entry.getKey())) {
            references.put(entry.getKey(), references.get(entry.getKey()).merge(entry.getValue()));
          } else {
            references.put(entry.getKey(), entry.getValue());
          }
        }

      } catch (final IOException e) {
        throw new IllegalStateException("Error reading class " + className, e);
      } finally {
        if (in != null) {
          try {
            in.close();
          } catch (final IOException e) {
            throw new IllegalStateException("Error closing class " + className, e);
          }
        }
      }
    }
    return references;
  }

  /**
   * Get the package of an internal class name.
   *
   * <p>foo/bar/Baz -> foo/bar/
   */
  private static String internalPackageName(final String internalName) {
    return internalName.replaceAll("/[^/]+$", "");
  }

  /**
   * Compute the minimum required access for FROM class to access the TO class.
   *
   * @return A reference flag with the required level of access.
   */
  private static Reference.Flag computeMinimumClassAccess(final Type from, final Type to) {
    if (from.getInternalName().equalsIgnoreCase(to.getInternalName())) {
      return Reference.Flag.PRIVATE_OR_HIGHER;
    } else if (internalPackageName(from.getInternalName())
        .equals(internalPackageName(to.getInternalName()))) {
      return Reference.Flag.PACKAGE_OR_HIGHER;
    } else {
      return Reference.Flag.PUBLIC;
    }
  }

  /**
   * Compute the minimum required access for FROM class to access a field on the TO class.
   *
   * @return A reference flag with the required level of access.
   */
  private static Reference.Flag computeMinimumFieldAccess(final Type from, final Type to) {
    if (from.getInternalName().equalsIgnoreCase(to.getInternalName())) {
      return Reference.Flag.PRIVATE_OR_HIGHER;
    } else if (internalPackageName(from.getInternalName())
        .equals(internalPackageName(to.getInternalName()))) {
      return Reference.Flag.PACKAGE_OR_HIGHER;
    } else {
      // Additional references: check the type hierarchy of FROM to distinguish public from
      // protected
      return Reference.Flag.PROTECTED_OR_HIGHER;
    }
  }

  /**
   * Compute the minimum required access for FROM class to access METHODTYPE on the TO class.
   *
   * @return A reference flag with the required level of access.
   */
  private static Reference.Flag computeMinimumMethodAccess(
      final Type from, final Type to, final Type methodType) {
    if (from.getInternalName().equalsIgnoreCase(to.getInternalName())) {
      return Reference.Flag.PRIVATE_OR_HIGHER;
    } else {
      // Additional references: check the type hierarchy of FROM to distinguish public from
      // protected
      return Reference.Flag.PROTECTED_OR_HIGHER;
    }
  }

  /**
   * @return If TYPE is an array, return the underlying type. If TYPE is not an array simply return
   *     the type.
   */
  private static Type underlyingType(Type type) {
    while (type.getSort() == Type.ARRAY) {
      type = type.getElementType();
    }
    return type;
  }

  private final Map<String, Reference> references = new HashMap<>();
  private String refSourceClassName;
  private Type refSourceType;
  private final boolean createFromMethodBodiesOnly;

  private ReferenceCreator(
      final ClassVisitor classVisitor, final boolean createFromMethodBodiesOnly) {
    super(Opcodes.ASM7, classVisitor);
    this.createFromMethodBodiesOnly = createFromMethodBodiesOnly;
  }

  public Map<String, Reference> getReferences() {
    return references;
  }

  private void addReference(final Reference ref) {
    if (references.containsKey(ref.getClassName())) {
      references.put(ref.getClassName(), references.get(ref.getClassName()).merge(ref));
    } else {
      references.put(ref.getClassName(), ref);
    }
  }

  @Override
  public void visit(
      final int version,
      final int access,
      final String name,
      final String signature,
      final String superName,
      final String[] interfaces) {
    refSourceClassName = Utils.getClassName(name);
    refSourceType = Type.getType("L" + name + ";");
    // Additional references we could check
    // - supertype of class and visible from this package
    // - interfaces of class and visible from this package
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public FieldVisitor visitField(
      final int access,
      final String name,
      final String descriptor,
      final String signature,
      final Object value) {
    // Additional references we could check
    // - annotations on field

    // intentionally not creating refs to fields here.
    // Will create refs in method instructions to include line numbers.
    return super.visitField(access, name, descriptor, signature, value);
  }

  @Override
  public MethodVisitor visitMethod(
      final int access,
      final String name,
      final String descriptor,
      final String signature,
      final String[] exceptions) {
    // Additional references we could check
    // - Classes in signature (return type, params) and visible from this package
    return new AdviceReferenceMethodVisitor(
        super.visitMethod(access, name, descriptor, signature, exceptions));
  }

  private class AdviceReferenceMethodVisitor extends MethodVisitor {
    private int currentLineNumber = -1;

    public AdviceReferenceMethodVisitor(final MethodVisitor methodVisitor) {
      super(Opcodes.ASM7, methodVisitor);
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
      currentLineNumber = line;
      super.visitLineNumber(line, start);
    }

    @Override
    public void visitFieldInsn(
        final int opcode, final String owner, final String name, final String descriptor) {
      // Additional references we could check
      // * DONE owner class
      //   * DONE owner class has a field (name)
      //   * DONE field is static or non-static
      //   * DONE field's visibility from this point (NON_PRIVATE?)
      // * DONE owner class's visibility from this point (NON_PRIVATE?)
      //
      // * DONE field-source class (descriptor)
      //   * DONE field-source visibility from this point (PRIVATE?)

      final Type ownerType =
          owner.startsWith("[")
              ? underlyingType(Type.getType(owner))
              : Type.getType("L" + owner + ";");
      final Type fieldType = Type.getType(descriptor);

      final List<Reference.Flag> fieldFlags = new ArrayList<>();
      fieldFlags.add(computeMinimumFieldAccess(refSourceType, ownerType));
      fieldFlags.add(
          opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC
              ? Reference.Flag.STATIC
              : Reference.Flag.NON_STATIC);

      addReference(
          new Reference.Builder(ownerType.getInternalName())
              .withSource(refSourceClassName, currentLineNumber)
              .withFlag(computeMinimumClassAccess(refSourceType, ownerType))
              .withField(
                  new Reference.Source[] {
                    new Reference.Source(refSourceClassName, currentLineNumber)
                  },
                  fieldFlags.toArray(new Reference.Flag[0]),
                  name,
                  fieldType)
              .build());

      final Type underlyingFieldType = underlyingType(fieldType);
      if (underlyingFieldType.getSort() == Type.OBJECT) {
        addReference(
            new Reference.Builder(underlyingFieldType.getInternalName())
                .withSource(refSourceClassName, currentLineNumber)
                .withFlag(computeMinimumClassAccess(refSourceType, underlyingFieldType))
                .build());
      }
      super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(
        final int opcode,
        final String owner,
        final String name,
        final String descriptor,
        final boolean isInterface) {
      // Additional references we could check
      // * DONE name of method owner's class
      //   * DONE is the owner an interface?
      //   * DONE owner's access from here (PRIVATE?)
      //   * DONE method on the owner class
      //   * DONE is the method static? Is it visible from here?
      // * Class names from the method descriptor
      //   * params classes
      //   * return type
      final Type methodType = Type.getMethodType(descriptor);

      { // ref for method return type
        final Type returnType = underlyingType(methodType.getReturnType());
        if (returnType.getSort() == Type.OBJECT) {
          addReference(
              new Reference.Builder(returnType.getInternalName())
                  .withSource(refSourceClassName, currentLineNumber)
                  .withFlag(computeMinimumClassAccess(refSourceType, returnType))
                  .build());
        }
      }
      // refs for method param types
      for (Type paramType : methodType.getArgumentTypes()) {
        paramType = underlyingType(paramType);
        if (paramType.getSort() == Type.OBJECT) {
          addReference(
              new Reference.Builder(paramType.getInternalName())
                  .withSource(refSourceClassName, currentLineNumber)
                  .withFlag(computeMinimumClassAccess(refSourceType, paramType))
                  .build());
        }
      }

      final Type ownerType =
          owner.startsWith("[")
              ? underlyingType(Type.getType(owner))
              : Type.getType("L" + owner + ";");

      final List<Reference.Flag> methodFlags = new ArrayList<>();
      methodFlags.add(
          opcode == Opcodes.INVOKESTATIC ? Reference.Flag.STATIC : Reference.Flag.NON_STATIC);
      methodFlags.add(computeMinimumMethodAccess(refSourceType, ownerType, methodType));

      addReference(
          new Reference.Builder(ownerType.getInternalName())
              .withSource(refSourceClassName, currentLineNumber)
              .withFlag(isInterface ? Reference.Flag.INTERFACE : Reference.Flag.NON_INTERFACE)
              .withFlag(computeMinimumClassAccess(refSourceType, ownerType))
              .withMethod(
                  new Reference.Source[] {
                    new Reference.Source(refSourceClassName, currentLineNumber)
                  },
                  methodFlags.toArray(new Reference.Flag[0]),
                  name,
                  methodType.getReturnType(),
                  methodType.getArgumentTypes())
              .build());
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitLdcInsn(final Object value) {
      if (value instanceof Type) {
        final Type type = underlyingType((Type) value);
        if (type.getSort() == Type.OBJECT) {
          addReference(
              new Reference.Builder(type.getInternalName())
                  .withSource(refSourceClassName, currentLineNumber)
                  .withFlag(computeMinimumClassAccess(refSourceType, type))
                  .build());
        }
      }
      super.visitLdcInsn(value);
    }
  }
}
