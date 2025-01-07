/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.bootstrap.InstrumentationProxy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.modifier.SyntheticState;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.InvokeDynamic;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.utility.JavaConstant;

/**
 * Factory for generating proxies which invoke their target via {@code INVOKEDYNAMIC}. Generated
 * proxy classes have the following properties: The generated proxies have the following basic
 * structure:
 *
 * <ul>
 *   <li>it has same superclass as the proxied class
 *   <li>it implements all interfaces implemented by the proxied class
 *   <li>for every public constructor of the proxied class, it defined a matching public constructor
 *       which:
 *       <ul>
 *         <li>invokes the default constructor of the superclass
 *         <li>invoked the corresponding constructor of the proxied class to generate the object to
 *             which the proxy delegates
 *       </ul>
 *   <li>it "copies" every declared static and non-static public method, the implementation will
 *       delegate to the corresponding method in the proxied class
 *   <li>all annotations on the proxied class and on its methods are copied to the proxy
 * </ul>
 *
 * <p>Note that only the public methods declared by the proxied class are actually proxied.
 * Inherited methods are not automatically proxied. If you want those to be proxied, you'll need to
 * explicitly override them in the proxied class.
 */
public class IndyProxyFactory {

  @FunctionalInterface
  public interface BootstrapArgsProvider {

    /**
     * Defines the additional arguments to pass to the invokedynamic bootstrap method for a given
     * proxied method. The arguments have to be storable in the constant pool.
     *
     * @param classBeingProxied the type for which {@link
     *     IndyProxyFactory#generateProxy(TypeDescription, String)} was invoked
     * @param proxiedMethodOrCtor the method or constructor from the proxied class for which the
     *     arguments are requested
     * @return the arguments to pass to the bootstrap method
     */
    List<? extends JavaConstant> getBootstrapArgsForMethod(
        TypeDescription classBeingProxied, MethodDescription.InDefinedShape proxiedMethodOrCtor);
  }

  private static final String DELEGATE_FIELD_NAME = "delegate";

  // Matches the single method of IndyProxy interface
  static final String PROXY_DELEGATE_NAME = "__getIndyProxyDelegate";

  private final MethodDescription.InDefinedShape indyBootstrapMethod;

  private final BootstrapArgsProvider bootstrapArgsProvider;

  public IndyProxyFactory(Method bootstrapMethod, BootstrapArgsProvider bootstrapArgsProvider) {
    this.indyBootstrapMethod = new MethodDescription.ForLoadedMethod(bootstrapMethod);
    this.bootstrapArgsProvider = bootstrapArgsProvider;
  }

  /**
   * Generates a proxy.
   *
   * @param classToProxy the class for which a proxy will be generated
   * @param proxyClassName the desired fully qualified name for the proxy class
   * @return the generated proxy class
   */
  public DynamicType.Unloaded<?> generateProxy(
      TypeDescription classToProxy, String proxyClassName) {
    TypeDescription.Generic superClass = classToProxy.getSuperClass();
    List<TypeDefinition> interfaces = new ArrayList<>(classToProxy.getInterfaces());
    interfaces.add(TypeDescription.ForLoadedType.of(InstrumentationProxy.class));
    DynamicType.Builder<?> builder =
        new ByteBuddy()
            .subclass(superClass, ConstructorStrategy.Default.NO_CONSTRUCTORS)
            .implement(interfaces)
            .name(proxyClassName)
            .annotateType(classToProxy.getDeclaredAnnotations())
            .defineField(DELEGATE_FIELD_NAME, Object.class, Modifier.PRIVATE | Modifier.FINAL);

    for (MethodDescription.InDefinedShape method : classToProxy.getDeclaredMethods()) {
      if (method.isPublic()) {
        if (method.isConstructor()) {
          List<? extends JavaConstant> bootstrapArgs =
              bootstrapArgsProvider.getBootstrapArgsForMethod(classToProxy, method);
          builder = createProxyConstructor(superClass, method, bootstrapArgs, builder);
        } else if (method.isMethod()) {
          List<? extends JavaConstant> bootstrapArgs =
              bootstrapArgsProvider.getBootstrapArgsForMethod(classToProxy, method);
          builder = createProxyMethod(method, bootstrapArgs, builder);
        }
      }
    }

    // Implement IndyProxy class and return the delegate field
    builder =
        builder
            .defineMethod(
                PROXY_DELEGATE_NAME, Object.class, Visibility.PUBLIC, SyntheticState.SYNTHETIC)
            .intercept(FieldAccessor.ofField(DELEGATE_FIELD_NAME));

    return builder.make();
  }

  private DynamicType.Builder<?> createProxyMethod(
      MethodDescription.InDefinedShape proxiedMethod,
      List<? extends JavaConstant> bootstrapArgs,
      DynamicType.Builder<?> builder) {
    InvokeDynamic body = InvokeDynamic.bootstrap(indyBootstrapMethod, bootstrapArgs);
    if (!proxiedMethod.isStatic()) {
      body = body.withField(DELEGATE_FIELD_NAME);
    }
    body = body.withMethodArguments();
    int modifiers = Modifier.PUBLIC | (proxiedMethod.isStatic() ? Modifier.STATIC : 0);
    return createProxyMethodOrConstructor(
        proxiedMethod,
        builder.defineMethod(proxiedMethod.getName(), proxiedMethod.getReturnType(), modifiers),
        body);
  }

  private DynamicType.Builder<?> createProxyConstructor(
      TypeDescription.Generic superClass,
      MethodDescription.InDefinedShape proxiedConstructor,
      List<? extends JavaConstant> bootstrapArgs,
      DynamicType.Builder<?> builder) {
    MethodDescription defaultSuperCtor = findDefaultConstructor(superClass);

    Implementation.Composable fieldAssignment =
        FieldAccessor.ofField(DELEGATE_FIELD_NAME)
            .setsValue(
                new StackManipulation.Compound(
                    MethodVariableAccess.allArgumentsOf(proxiedConstructor),
                    MethodInvocation.invoke(indyBootstrapMethod)
                        .dynamic(
                            "ctor", // the actual <init> method name is not allowed by the verifier
                            TypeDescription.ForLoadedType.of(Object.class),
                            proxiedConstructor.getParameters().asTypeList().asErasures(),
                            bootstrapArgs)),
                Object.class);
    Implementation.Composable ctorBody =
        MethodCall.invoke(defaultSuperCtor).andThen(fieldAssignment);
    return createProxyMethodOrConstructor(
        proxiedConstructor, builder.defineConstructor(Modifier.PUBLIC), ctorBody);
  }

  private static MethodDescription findDefaultConstructor(TypeDescription.Generic superClass) {
    return superClass.getDeclaredMethods().stream()
        .filter(MethodDescription::isConstructor)
        .filter(constructor -> constructor.getParameters().isEmpty())
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Superclass of provided type does not define a default constructor"));
  }

  private static DynamicType.Builder<?> createProxyMethodOrConstructor(
      MethodDescription.InDefinedShape method,
      DynamicType.Builder.MethodDefinition.ParameterDefinition<?> methodDef,
      Implementation methodBody) {
    for (ParameterDescription param : method.getParameters()) {
      methodDef =
          methodDef
              .withParameter(param.getType(), param.getName(), param.getModifiers())
              .annotateParameter(param.getDeclaredAnnotations());
    }
    return methodDef
        .throwing(method.getExceptionTypes())
        .intercept(methodBody)
        .annotateMethod(method.getDeclaredAnnotations());
  }
}
