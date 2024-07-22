package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This transformer is a workaround to make the life of instrumentation developers easier.
 *
 * When inserting the instrumentation and calling the advice enter/exit methods,
 * we insert an invokedynamic instruction to perform this method call.
 * This instruction has a {@link java.lang.invoke.MethodType} associated, which normally corresponds to the method type of the called advice.
 * This can however lead to problems, when the advice method signature contains types which are not visible to the instrumented class.
 *
 * To prevent this, we instead associate the invokedynamic instruction with a type where all class reference are replaced with references
 * to {@link Object}. To lookup the correct advice method, we pass the original type as string argument to the invokedynamic bootstrapping method.
 *
 * Because bytebuddy currently doesn't support this customization, we perform the type erasure on the .class via ASM before bytebuddy parses the advice.
 * In addition, we insert an annotation to preserve the original descriptor of the method.
 */
//TODO: replace this workaround when buddy natively supports altering the MethodType for invokedynamic advices
public class AdviceSignatureEraser {

  private static final Pattern TYPE_REFERENCE_PATTERN = Pattern.compile("L[^;]+;");


  private AdviceSignatureEraser(){}

  static byte[] transform(byte[] bytes) {
    ClassReader cr = new ClassReader(bytes);
    ClassWriter cw = new ClassWriter(cr, 0);
    ClassNode classNode = new ClassNode();
    cr.accept(classNode, 0);

    // skip the class if there aren't any methods with advice annotations or these annotations have
    // set inline = false
    Set<String> methodsToTransform = listAdviceMethods(classNode);
    if (methodsToTransform.isEmpty()) {
      classNode.accept(cw);
      return cw.toByteArray();
    }

    ClassVisitor cv =
        new ClassVisitor(AsmApi.VERSION, cw) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            if (methodsToTransform.contains(name+descriptor)) {
              String erased = eraseTypes(descriptor);
              MethodVisitor visitor = super.visitMethod(access, name, erased, null, exceptions);
              AnnotationVisitor av = visitor.visitAnnotation(
                  "Lio/opentelemetry/javaagent/tooling/instrumentation/indy/OriginalDescriptor;",
                  false);
              av.visit("value", descriptor);
              av.visitEnd();
              return visitor;
            } else {
              return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
          }
        };
    classNode.accept(cv);
    return cw.toByteArray();
  }

  private static String eraseTypes(String descriptor) {
    Matcher matcher = TYPE_REFERENCE_PATTERN.matcher(descriptor);
    return matcher.replaceAll("Ljava/lang/Object;");
  }

  private static Set<String> listAdviceMethods(ClassNode classNode) {
    return classNode.methods.stream()
        .filter(mn -> AdviceTransformer.hasAnnotation(mn, AdviceTransformer.ADVICE_ON_METHOD_ENTER)
            || AdviceTransformer.hasAnnotation(mn, AdviceTransformer.ADVICE_ON_METHOD_EXIT))
        .map(mn -> mn.name + mn.desc)
        .collect(Collectors.toSet());
  }

}
