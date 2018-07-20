package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Utils;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import datadog.trace.agent.tooling.muzzle.Reference.Method;
import datadog.trace.agent.tooling.muzzle.Reference.Mismatch;
import datadog.trace.agent.tooling.muzzle.Reference.Source;
import datadog.trace.agent.tooling.muzzle.Reference.Field;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class UnloadedType extends ClassVisitor {
  private static final Map<ClassLoader, Map<String, UnloadedType>> typeCache = Collections.synchronizedMap(new WeakHashMap<ClassLoader, Map<String, UnloadedType>>());

  private volatile String superName = null;
  private volatile String className = null;
  private volatile String[] interfaceNames = new String[0];
  private volatile UnloadedType unloadedSuper = null;
  private final List<UnloadedType> unloadedInterfaces = new ArrayList<>();
  private final List<Method> methods = new ArrayList<>();

  public static UnloadedType of(String className, ClassLoader classLoader) throws Exception {
    className = Utils.getInternalName(className);
    // TODO: triple-check cache logic and weak-refs
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
      for (String interfaceName : unloadedType.interfaceNames)  {
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

  public List<Mismatch> checkMatch(Method method) {
    final List<Mismatch> mismatches = new ArrayList<>(0);
    // does the method exist?
    if (!hasMethod(method)) {
      mismatches.add(new Mismatch.MissingMethod(method.getSources().toArray(new Source[0]), className, method.toString()));
    } else {
      // TODO: are the expected method flags present (static, public, etc)
    }
    return mismatches;
  }

  private boolean hasMethod(Method method) {
    if (methods.contains(method)) {
      return true;
    }
    // FIXME: private methods on the super type are not reachable!
    if (null != unloadedSuper && unloadedSuper.hasMethod(method)) {
      return true;
    }
    for (UnloadedType unloadedInterface : unloadedInterfaces) {
      if (unloadedInterface.hasMethod(method)) { return true; }
    }
    return false;
  }

  public boolean hasField(Field field) {
    // TODO does the field exist?
    // TODO are the expected field flags present (static, public, etc)
    throw new RuntimeException("TODO");
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
    super.visit(version, access, name, signature, superName, interfaces);
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
    methods.add(new Reference.Method(name, descriptor));
    return super.visitMethod(access, name, descriptor, signature, exceptions);
  }

}
