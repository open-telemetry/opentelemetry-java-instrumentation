package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Visit a class and collect all references made by the visited class. */
public class AdviceReferenceVisitor extends ClassVisitor {
  private Map<String, Reference> references = new HashMap<>();
  private String refSourceClassName;

  public AdviceReferenceVisitor(ClassVisitor classVisitor) {
    super(Opcodes.ASM6, classVisitor);
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
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(
      final int access,
      final String name,
      final String descriptor,
      final String signature,
      final String[] exceptions) {
    return new AdviceReferenceMethodVisitor(
        super.visitMethod(access, name, descriptor, signature, exceptions));
  }

  private class AdviceReferenceMethodVisitor extends MethodVisitor {
    private boolean isAdviceMethod = false;
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
    public void visitMethodInsn(
        final int opcode,
        final String owner,
        final String name,
        final String descriptor,
        final boolean isInterface) {
      addReference(
          new Reference(
              new Reference.Source[] {new Reference.Source(refSourceClassName, currentLineNumber)},
              Utils.getClassName(owner),
              null,
              null));
    }
  }

  public static Map<String, Reference> createReferencesFrom(
      String entryPointClassName, ClassLoader loader) {
    final Set<String> visitedSources = new HashSet<String>();
    final Map<String, Reference> references = new HashMap<>();

    final Queue<String> instrumentationQueue = new ArrayDeque<>();
    instrumentationQueue.add(entryPointClassName);

    while (!instrumentationQueue.isEmpty()) {
      final String className = instrumentationQueue.remove();
      visitedSources.add(className);
      try {
        final InputStream in =
            ReferenceMatcher.class
                .getClassLoader()
                .getResourceAsStream(Utils.getResourceName(className));
        try {
          final AdviceReferenceVisitor cv = new AdviceReferenceVisitor(null);
          final ClassReader reader = new ClassReader(in);
          reader.accept(cv, ClassReader.SKIP_FRAMES);

          Map<String, Reference> instrumentationReferences = cv.getReferences();
          for (Map.Entry<String, Reference> entry : instrumentationReferences.entrySet()) {
            // TODO: expose config instead of hardcoding datadog instrumentation namespace
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
          in.close();
        }
      } catch (IOException ioe) {
        throw new IllegalStateException(ioe);
      }
    }
    return references;
  }
}
