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
  public static Map<String, Reference> createReferencesFrom(
      String entryPointClassName, ClassLoader loader) {
    return ReferenceCreator.createReferencesFrom(entryPointClassName, loader, true);
  }

  /**
   * Generate all references reachable from a given class.
   *
   * @param entryPointClassName Starting point for generating references.
   * @param loader Classloader used to read class bytes.
   * @param startFromMethodBodies if true only create refs from method bodies.
   * @return Map of [referenceClassName -> Reference]
   */
  private static Map<String, Reference> createReferencesFrom(
      String entryPointClassName, ClassLoader loader, boolean startFromMethodBodies) {
    final Set<String> visitedSources = new HashSet<>();
    final Map<String, Reference> references = new HashMap<>();

    final Queue<String> instrumentationQueue = new ArrayDeque<>();
    instrumentationQueue.add(entryPointClassName);

    while (!instrumentationQueue.isEmpty()) {
      final String className = instrumentationQueue.remove();
      visitedSources.add(className);
      try {
        final InputStream in = loader.getResourceAsStream(Utils.getResourceName(className));
        try {
          final ReferenceCreator cv = new ReferenceCreator(null, startFromMethodBodies);
          // only start from method bodies on the first pass
          startFromMethodBodies = false;
          final ClassReader reader = new ClassReader(in);
          reader.accept(cv, ClassReader.SKIP_FRAMES);

          Map<String, Reference> instrumentationReferences = cv.getReferences();
          for (Map.Entry<String, Reference> entry : instrumentationReferences.entrySet()) {
            // Don't generate references created outside of the datadog instrumentation package.
            if (!visitedSources.contains(entry.getKey())
                && entry.getKey().startsWith("datadog.trace.instrumentation.")) {
              instrumentationQueue.add(entry.getKey());
            }
            if (references.containsKey(entry.getKey())) {
              references.put(
                  entry.getKey(), references.get(entry.getKey()).merge(entry.getValue()));
            } else {
              references.put(entry.getKey(), entry.getValue());
            }
          }

        } finally {
          if (in != null) {
            in.close();
          }
        }
      } catch (IOException ioe) {
        throw new IllegalStateException(ioe);
      }
    }
    return references;
  }

  private static String internalPackageName(String internalName) {
    return internalName.replaceAll("/[^/]+$", "");
  }

  /**
   * Compute the minimum required access for FROM class to access the TO class.
   *
   * @return A reference flag with the required level of access.
   */
  private static Reference.Flag computeMinimumClassAccess(Type from, Type to) {
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
   * Compute the minimum required access for FROM class to access METHODTYPE on the TO class.
   *
   * @return A reference flag with the required level of access.
   */
  private static Reference.Flag computeMinimumMethodAccess(Type from, Type to, Type methodType) {
    if (from.getInternalName().equalsIgnoreCase(to.getInternalName())) {
      return Reference.Flag.PRIVATE_OR_HIGHER;
    } else {
      // Additional references: check the type hierarchy of FROM to distinguish public from
      // protected
      return Reference.Flag.PROTECTED_OR_HIGHER;
    }
  }

  private Map<String, Reference> references = new HashMap<>();
  private String refSourceClassName;
  private Type refSourceType;
  private boolean createFromMethodBodiesOnly;

  private ReferenceCreator(ClassVisitor classVisitor, boolean createFromMethodBodiesOnly) {
    super(Opcodes.ASM6, classVisitor);
    this.createFromMethodBodiesOnly = createFromMethodBodiesOnly;
  }

  public Map<String, Reference> getReferences() {
    return references;
  }

  private void addReference(Reference ref) {
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
      int access, String name, String descriptor, String signature, Object value) {
    // Additional references we could check
    // - type of field + visible from this package
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

    public AdviceReferenceMethodVisitor(MethodVisitor methodVisitor) {
      super(Opcodes.ASM6, methodVisitor);
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
      currentLineNumber = line;
      super.visitLineNumber(line, start);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      // Additional references we could check
      // * DONE owner class
      //   * owner class has a field (name)
      //   * field is static or non-static
      //   * field's visibility from this point (NON_PRIVATE?)
      // * owner class's visibility from this point (NON_PRIVATE?)
      //
      // * DONE field-source class (descriptor)
      //   * field-source visibility from this point (PRIVATE?)

      // owning class has a field
      addReference(
          new Reference.Builder(owner).withSource(refSourceClassName, currentLineNumber).build());
      Type fieldType = Type.getType(descriptor);
      if (fieldType.getSort() == Type.ARRAY) {
        fieldType = fieldType.getElementType();
      }
      if (fieldType.getSort() == Type.OBJECT) {
        addReference(
            new Reference.Builder(fieldType.getInternalName())
                .withSource(refSourceClassName, currentLineNumber)
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
        Type returnType = methodType.getReturnType();
        if (returnType.getSort() == Type.ARRAY) {
          returnType = returnType.getElementType();
        }
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
        if (paramType.getSort() == Type.ARRAY) {
          paramType = paramType.getElementType();
        }
        if (paramType.getSort() == Type.OBJECT) {
          addReference(
              new Reference.Builder(paramType.getInternalName())
                  .withSource(refSourceClassName, currentLineNumber)
                  .withFlag(computeMinimumClassAccess(refSourceType, paramType))
                  .build());
        }
      }

      Type ownerType = Type.getType("L" + owner + ";");
      if (ownerType.getSort() == Type.ARRAY) {
        ownerType = ownerType.getElementType();
      }

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
  }
}
