package datadog.trace.agent.tooling.context;

import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * InstrumentationContextProvider which stores context in a global map.
 */
public class MapBackedProvider implements InstrumentationContextProvider {
  private final Instrumenter.Default instrumenter;

  public MapBackedProvider(Instrumenter.Default instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public AsmVisitorWrapper getInstrumentationVisitor() {
    return new AsmVisitorWrapper() {
      @Override
      public int mergeWriter(int flags) {
        // FIXME: any writer flags needed?
        return flags;
      }

      @Override
      public int mergeReader(int flags) {
        // FIXME: any reader flags needed?
        return flags;
      }

      @Override
      public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor, Implementation.Context implementationContext, TypePool typePool, FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods, int writerFlags, int readerFlags) {
        return new ClassVisitor(Opcodes.ASM7, classVisitor) {
          @Override
          public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
          }

          @Override
          public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // TODO
            System.out.println(instrumenter.instrumentationPrimaryName + " context visit method: " + name + descriptor);
            for (Map.Entry<String, String> entry : instrumenter.contextStore().entrySet()) {
              System.out.println("  -- remap: " + entry.getKey() + " -> " + entry.getValue());
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
          }
        };
      }
    };
  }

  @Override
  public List<byte[]> dynamicClasses() {
    // TODO: create a map-holder class for each key in the context-store
    return Collections.emptyList();
  }

  @Override
  public AgentBuilder additionalInstrumentation(AgentBuilder builder) {
    return builder;
  }

  /**
   * Template class used to generate the class holding the global map.
   */
  private static final class MapHolder {
  }
}
