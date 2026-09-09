/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.FaasIncubatingAttributes.FAAS_INVOCATION_ID;
import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AwsLambdaRequestHandlerTest implements Opcodes {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Mock private Context context;

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/19711
  @Test
  void testReturnPrimitiveVoid() throws Exception {
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");

    RequestHandler<Object, Object> handler = generate().getConstructor().newInstance();
    handler.handleRequest("hello", context);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(equalTo(FAAS_INVOCATION_ID, "1-22-333"))));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static Class<? extends RequestHandler<Object, Object>> generate() throws Exception {
    String testClassSlashName = "test/TestLambdaRequestHandler";
    String testClassDotName = testClassSlashName.replace('/', '.');
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

    writer.visit(
        V1_8,
        ACC_PUBLIC | ACC_SUPER,
        testClassSlashName,
        null,
        "java/lang/Object",
        new String[] {Type.getInternalName(RequestHandler.class)});

    // generates constructor
    {
      MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);

      method.visitCode();

      method.visitVarInsn(ALOAD, 0);
      method.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      method.visitInsn(RETURN);

      method.visitMaxs(0, 0);
      method.visitEnd();
    }

    // generates public void handleRequest(Object input, Context context)
    {
      MethodVisitor method =
          writer.visitMethod(
              ACC_PUBLIC,
              "handleRequest",
              "(Ljava/lang/Object;L" + Type.getInternalName(Context.class) + ";)V",
              null,
              null);

      method.visitCode();
      method.visitInsn(RETURN);

      method.visitMaxs(0, 0);
      method.visitEnd();
    }

    /*
     * Generates:
     *
     * public synthetic bridge Object handleRequest(Object input, Context context) {
     *   handleRequest(input, context);
     *   return null;
     * }
     */
    {
      MethodVisitor method =
          writer.visitMethod(
              ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC,
              "handleRequest",
              "(Ljava/lang/Object;L" + Type.getInternalName(Context.class) + ";)Ljava/lang/Object;",
              null,
              null);

      method.visitCode();

      // this.handleRequest(input, context);
      method.visitVarInsn(ALOAD, 0);
      method.visitVarInsn(ALOAD, 1);
      method.visitVarInsn(ALOAD, 2);

      method.visitMethodInsn(
          INVOKEVIRTUAL,
          testClassSlashName,
          "handleRequest",
          "(Ljava/lang/Object;L" + Type.getInternalName(Context.class) + ";)V",
          false);

      // return null;
      method.visitInsn(ACONST_NULL);
      method.visitInsn(ARETURN);

      method.visitMaxs(0, 0);
      method.visitEnd();
    }

    writer.visitEnd();
    ClassLoader classLoader =
        new ByteArrayClassLoader(
            AwsLambdaRequestHandlerTest.class.getClassLoader(),
            singletonMap(testClassDotName, writer.toByteArray()));
    return (Class<? extends RequestHandler<Object, Object>>)
        classLoader.loadClass(testClassDotName);
  }
}
