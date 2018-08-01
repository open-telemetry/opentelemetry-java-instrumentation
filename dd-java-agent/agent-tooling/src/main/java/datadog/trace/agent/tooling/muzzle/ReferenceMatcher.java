package datadog.trace.agent.tooling.muzzle;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;
import static datadog.trace.bootstrap.WeakMap.Provider.newWeakMap;
import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.BOOTSTRAP_LOADER;

import datadog.trace.agent.tooling.Utils;
import datadog.trace.agent.tooling.muzzle.Reference.Mismatch;
import datadog.trace.agent.tooling.muzzle.Reference.Source;
import datadog.trace.bootstrap.WeakMap;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

/** Matches a set of references against a classloader. */
@Slf4j
public class ReferenceMatcher {
  private final WeakMap<ClassLoader, List<Reference.Mismatch>> mismatchCache = newWeakMap();
  private final Reference[] references;
  private final Set<String> helperClassNames;

  public ReferenceMatcher(final Reference... references) {
    this(new String[0], references);
  }

  public ReferenceMatcher(final String[] helperClassNames, final Reference[] references) {
    this.references = references;
    this.helperClassNames = new HashSet<>(Arrays.asList(helperClassNames));
  }

  /**
   * @param loader Classloader to validate against (or null for bootstrap)
   * @return true if all references match the classpath of loader
   */
  public boolean matches(final ClassLoader loader) {
    return getMismatchedReferenceSources(loader).size() == 0;
  }

  /**
   * @param loader Classloader to validate against (or null for bootstrap)
   * @return A list of all mismatches between this ReferenceMatcher and loader's classpath.
   */
  public List<Reference.Mismatch> getMismatchedReferenceSources(ClassLoader loader) {
    if (loader == BOOTSTRAP_LOADER) {
      loader = Utils.getBootstrapProxy();
    }
    List<Reference.Mismatch> mismatches = mismatchCache.get(loader);
    if (null == mismatches) {
      synchronized (loader) {
        mismatches = mismatchCache.get(loader);
        if (null == mismatches) {
          mismatches = new ArrayList<>(0);
          for (final Reference reference : references) {
            // Don't reference-check helper classes.
            // They will be injected by the instrumentation's HelperInjector.
            if (!helperClassNames.contains(reference.getClassName())) {
              mismatches.addAll(checkMatch(reference, loader));
            }
          }
          mismatchCache.put(loader, mismatches);
        }
      }
    }
    return mismatches;
  }

  /**
   * Check a reference against a classloader's classpath.
   *
   * @param loader
   * @return A list of mismatched sources. A list of size 0 means the reference matches the class.
   */
  private static List<Reference.Mismatch> checkMatch(Reference reference, ClassLoader loader) {
    if (loader == BOOTSTRAP_CLASSLOADER) {
      throw new IllegalStateException("Cannot directly check against bootstrap classloader");
    }
    if (!onClasspath(reference.getClassName(), loader)) {
      return Collections.<Mismatch>singletonList(
          new Mismatch.MissingClass(
              reference.getSources().toArray(new Source[0]), reference.getClassName()));
    }
    final List<Mismatch> mismatches = new ArrayList<>(0);
    try {
      ReferenceMatcher.UnloadedType typeOnClasspath =
          ReferenceMatcher.UnloadedType.of(reference.getClassName(), loader);
      mismatches.addAll(typeOnClasspath.checkMatch(reference));
      for (Reference.Method requiredMethod : reference.getMethods()) {
        mismatches.addAll(typeOnClasspath.checkMatch(requiredMethod));
      }
    } catch (Exception e) {
      // Shouldn't happen. Fail the reference check and add a mismatch for debug logging.
      mismatches.add(new Mismatch.ReferenceCheckError(e));
    }
    return mismatches;
  }

  private static boolean onClasspath(final String className, final ClassLoader loader) {
    final String resourceName = Utils.getResourceName(className);
    return loader.getResource(resourceName) != null
        // we can also reach bootstrap classes
        || Utils.getBootstrapProxy().getResource(resourceName) != null;
  }

  /**
   * A representation of a jvm class created from a byte array without loading the class in
   * question.
   *
   * <p>Used to compare an expected Reference with the actual runtime class without causing
   * classloads.
   */
  public static class UnloadedType extends ClassVisitor {
    private static final Map<ClassLoader, Map<String, UnloadedType>> typeCache =
        Collections.synchronizedMap(new WeakHashMap<ClassLoader, Map<String, UnloadedType>>());

    private String superName = null;
    private String className = null;
    private String[] interfaceNames = new String[0];
    private UnloadedType unloadedSuper = null;
    private final List<UnloadedType> unloadedInterfaces = new ArrayList<>();
    private int flags;
    private final List<Method> methods = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();

    public static UnloadedType of(String className, ClassLoader classLoader) throws Exception {
      className = Utils.getInternalName(className);
      Map<String, UnloadedType> classLoaderCache = typeCache.get(classLoader);
      if (classLoaderCache == null) {
        synchronized (classLoader) {
          classLoaderCache = typeCache.get(classLoader);
          if (classLoaderCache == null) {
            classLoaderCache = new ConcurrentHashMap<>();
            typeCache.put(classLoader, classLoaderCache);
          }
        }
      }
      UnloadedType unloadedType = classLoaderCache.get(className);
      if (unloadedType == null) {
        final InputStream in = classLoader.getResourceAsStream(Utils.getResourceName(className));
        unloadedType = new UnloadedType(null);
        final ClassReader reader = new ClassReader(in);
        reader.accept(unloadedType, ClassReader.SKIP_CODE);
        if (unloadedType.superName != null) {
          unloadedType.unloadedSuper = UnloadedType.of(unloadedType.superName, classLoader);
        }
        for (String interfaceName : unloadedType.interfaceNames) {
          unloadedType.unloadedInterfaces.add(UnloadedType.of(interfaceName, classLoader));
        }
        classLoaderCache.put(className, unloadedType);
      }
      return unloadedType;
    }

    private UnloadedType(ClassVisitor cv) {
      super(Opcodes.ASM6, cv);
    }

    public String getClassName() {
      return className;
    }

    public String getSuperName() {
      return superName;
    }

    public int getFlags() {
      return flags;
    }

    public List<Reference.Mismatch> checkMatch(Reference reference) {
      final List<Reference.Mismatch> mismatches = new ArrayList<>(0);
      for (Reference.Flag flag : reference.getFlags()) {
        if (!flag.matches(getFlags())) {
          final String desc = this.getClassName();
          mismatches.add(
              new Mismatch.MissingFlag(
                  reference.getSources().toArray(new Source[0]), desc, flag, getFlags()));
        }
      }
      return mismatches;
    }

    public List<Reference.Mismatch> checkMatch(Reference.Field fieldRef) {
      final List<Reference.Mismatch> mismatches = new ArrayList<>(0);
      final Field unloadedField = findField(fieldRef, true);
      if (unloadedField == null) {
        mismatches.add(
            new Reference.Mismatch.MissingField(
                fieldRef.getSources().toArray(new Reference.Source[0]),
                className,
                fieldRef.getName(),
                fieldRef.getType().getInternalName()));
      } else {
        for (Reference.Flag flag : fieldRef.getFlags()) {
          if (!flag.matches(unloadedField.getFlags())) {
            final String desc = this.getClassName() + "#" + unloadedField.signature;
            mismatches.add(
                new Mismatch.MissingFlag(
                    fieldRef.getSources().toArray(new Source[0]),
                    desc,
                    flag,
                    unloadedField.getFlags()));
          }
        }
      }
      return mismatches;
    }

    public List<Reference.Mismatch> checkMatch(Reference.Method methodRef) {
      final List<Reference.Mismatch> mismatches = new ArrayList<>(0);
      final Method unloadedMethod = findMethod(methodRef, true);
      if (unloadedMethod == null) {
        mismatches.add(
            new Reference.Mismatch.MissingMethod(
                methodRef.getSources().toArray(new Reference.Source[0]),
                className,
                methodRef.toString()));
      } else {
        for (Reference.Flag flag : methodRef.getFlags()) {
          if (!flag.matches(unloadedMethod.getFlags())) {
            final String desc = this.getClassName() + "#" + unloadedMethod.signature;
            mismatches.add(
                new Mismatch.MissingFlag(
                    methodRef.getSources().toArray(new Source[0]),
                    desc,
                    flag,
                    unloadedMethod.getFlags()));
          }
        }
      }
      return mismatches;
    }

    private Method findMethod(Reference.Method method, boolean includePrivateMethods) {
      Method unloadedMethod =
          new Method(
              0,
              method.getName(),
              Type.getMethodType(
                      method.getReturnType(), method.getParameterTypes().toArray(new Type[0]))
                  .getDescriptor());
      for (Method meth : methods) {
        if (meth.equals(unloadedMethod)) {
          if (meth.is(Opcodes.ACC_PRIVATE)) {
            return includePrivateMethods ? meth : null;
          } else {
            return meth;
          }
        }
      }
      if (null != unloadedSuper) {
        final Method meth = unloadedSuper.findMethod(method, false);
        if (null != meth) return meth;
      }
      for (UnloadedType unloadedInterface : unloadedInterfaces) {
        final Method meth = unloadedInterface.findMethod(method, false);
        if (null != meth) return meth;
      }
      return null;
    }

    private Field findField(Reference.Field fieldRef, boolean includePrivateFields) {
      final Field key = new Field(0, fieldRef.getName(), fieldRef.getType().getDescriptor());
      final int index = fields.indexOf(key);
      if (index != -1) {
        final Field foundField = fields.get(index);
        if (foundField.is(Opcodes.ACC_PRIVATE)) {
          return includePrivateFields ? foundField : null;
        } else {
          return foundField;
        }
      } else {
        Field superField = null;
        if (unloadedSuper != null) {
          superField = unloadedSuper.findField(fieldRef, false);
          if (superField != null) {
            return superField;
          }
        }
        for (UnloadedType unloadedInterface : unloadedInterfaces) {
          superField = unloadedInterface.findField(fieldRef, false);
          if (superField != null) {
            return superField;
          }
        }
      }
      return null;
    }

    @Override
    public void visit(
        final int version,
        final int access,
        final String name,
        final String signature,
        final String superName,
        final String[] interfaces) {
      className = Utils.getClassName(name);
      if (null != superName) this.superName = Utils.getClassName(superName);
      if (null != interfaces) this.interfaceNames = interfaces;
      this.flags = access;
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      fields.add(new Field(access, name, descriptor));
      return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
        final int access,
        final String name,
        final String descriptor,
        final String signature,
        final String[] exceptions) {
      methods.add(new Method(access, name, descriptor));
      return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    private static class Method {
      private final int flags;
      // name + descriptor
      private final String signature;

      public Method(int flags, String name, String desc) {
        this.flags = flags;
        this.signature = name + desc;
      }

      public boolean is(int flag) {
        boolean result = (flags & flag) != 0;
        return result;
      }

      public int getFlags() {
        return flags;
      }

      @Override
      public String toString() {
        return new StringBuilder("Unloaded: ").append(signature).toString();
      }

      @Override
      public boolean equals(Object o) {
        if (o instanceof Method) {
          return signature.equals(((Method) o).signature);
        }
        return false;
      }

      @Override
      public int hashCode() {
        return signature.hashCode();
      }
    }

    private static class Field {
      private final int flags;
      // name + typeDesc
      private final String signature;

      public Field(int flags, String name, String typeDesc) {
        this.flags = flags;
        this.signature = name + typeDesc;
      }

      private int getFlags() {
        return flags;
      }

      public boolean is(int flag) {
        boolean result = (flags & flag) != 0;
        return result;
      }

      @Override
      public String toString() {
        return new StringBuilder("Unloaded: ").append(signature).toString();
      }

      @Override
      public boolean equals(Object o) {
        if (o instanceof Field) {
          return signature.equals(((Field) o).signature);
        }
        return false;
      }

      @Override
      public int hashCode() {
        return signature.hashCode();
      }
    }
  }
}
