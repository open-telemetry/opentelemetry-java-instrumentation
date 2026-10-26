/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.test.annotation.packageprivate.PackagePrivateOverride;
import io.opentelemetry.test.annotation.packageprivate.PublicOverride;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class InheritedWithSpanInstrumentationTest {

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  interface Interface {
    @WithSpan
    String plain();

    @WithSpan(value = "interfaceName", kind = SpanKind.PRODUCER)
    String named();

    @WithSpan
    String withAttributes(@SpanAttribute("value") String value, String notTraced);
  }

  interface GenericInterface<T> {
    @WithSpan
    T handle(@SpanAttribute("message") T message);
  }

  abstract static class AbstractImplementationOfInterface implements Interface {
    @Override
    public String plain() {
      return "abstract";
    }
  }

  static class ImplementationOfInterface extends AbstractImplementationOfInterface {
    @Override
    public String named() {
      return "hello!";
    }

    @Override
    public String withAttributes(String value, String notTraced) {
      return value;
    }
  }

  static class ImplementationOfGenericInterface implements GenericInterface<String> {
    @Override
    public String handle(String message) {
      return message;
    }
  }

  static class ExtendedImplementationOfGenericInterface extends ImplementationOfGenericInterface {
    @Override
    public String handle(String message) {
      return message;
    }
  }

  static class OverridingImplementationOfInterface implements Interface {
    @Override
    public String plain() {
      return "hello!";
    }

    @Override
    @WithSpan("implName")
    public String named() {
      return "hello!";
    }

    @Override
    public String withAttributes(String value, String notTraced) {
      return value;
    }
  }

  interface InterfaceWithAttributes {
    @WithSpan
    String childAttribute(String value);

    @WithSpan
    String split(@SpanAttribute("first") String first, String second);

    @WithSpan
    String overridden(@SpanAttribute("parent") String value);

    String notTraced();
  }

  static class ImplementationOfInterfaceWithAttributes implements InterfaceWithAttributes {
    @Override
    public String childAttribute(@SpanAttribute("value") String value) {
      return value;
    }

    @Override
    public String split(String first, @SpanAttribute("second") String second) {
      return first + second;
    }

    @Override
    public String overridden(@SpanAttribute("child") String value) {
      return value;
    }

    @Override
    public String notTraced() {
      return "hello!";
    }
  }

  interface InterfaceWithOverloads {
    @WithSpan
    String overloaded(Object value);

    String overloaded(String value);
  }

  static class ImplementationOfInterfaceWithOverloads implements InterfaceWithOverloads {
    @Override
    public String overloaded(Object value) {
      return "object";
    }

    @Override
    public String overloaded(String value) {
      return "string";
    }
  }

  interface GenericInterfaceWithOverloads<T> {
    @WithSpan
    String handle(T value);
  }

  static class ImplementationOfGenericInterfaceWithOverloads
      implements GenericInterfaceWithOverloads<String> {
    @Override
    public String handle(String value) {
      return "string";
    }

    public String handle(Integer value) {
      return "integer";
    }
  }

  static class PrivateBase {
    final String name = "base";

    @WithSpan
    private String work() {
      return name;
    }

    String callBase() {
      return work();
    }
  }

  static class PrivateSub extends PrivateBase {
    private String work() {
      return name + "sub";
    }

    String callSub() {
      return work();
    }
  }

  interface InterfaceWithObjectParameter {
    @WithSpan
    String run(Object value);
  }

  static class ImplementationWithNonOverridingOverload implements InterfaceWithObjectParameter {
    @Override
    public String run(Object value) {
      return "object";
    }

    @AddingSpanAttributes
    public String run(@SpanAttribute("value") String value) {
      return value;
    }

    @WithSpan
    String parent() {
      return run("a");
    }
  }

  static class OuterWithGenericBase<T> {
    class InnerBase {
      @WithSpan
      String handle(@SpanAttribute("value") T value) {
        return "base";
      }
    }
  }

  static class ImplementationOfInnerBase extends OuterWithGenericBase<String>.InnerBase {
    ImplementationOfInnerBase(OuterWithGenericBase<String> outer) {
      outer.super();
    }

    @Override
    String handle(String value) {
      return value;
    }
  }

  static class OverrideInOtherPackage extends PublicOverride {
    @Override
    public String run() {
      return "other";
    }
  }

  static class PublicOverrideOfPackagePrivateOverride extends PackagePrivateOverride {
    public String run() {
      return "public";
    }
  }

  interface GenericRunner<T> {
    @WithSpan
    String run(T value);
  }

  static class ImplementationWithUnrelatedGenericOverload implements GenericRunner<String> {
    @Override
    public String run(String value) {
      return value;
    }

    @AddingSpanAttributes
    public String run(@SpanAttribute("value") Integer value) {
      return String.valueOf(value);
    }

    @WithSpan
    String parent() {
      return run(1);
    }
  }

  interface BoundedGenericInterface<T extends Number> {
    @WithSpan
    String bounded(@SpanAttribute("number") T number);
  }

  static class ImplementationOfBoundedGenericInterface implements BoundedGenericInterface<Integer> {
    @Override
    public String bounded(Integer number) {
      return String.valueOf(number);
    }
  }

  interface ArrayGenericInterface<T> {
    @WithSpan
    String array(T[] values);
  }

  static class ImplementationOfArrayGenericInterface implements ArrayGenericInterface<String> {
    @Override
    public String array(String[] values) {
      return String.join(",", values);
    }
  }

  interface CovariantInterface {
    @WithSpan
    Object value();
  }

  static class ImplementationOfCovariantInterface implements CovariantInterface {
    @Override
    public String value() {
      return "hello!";
    }
  }

  interface ExtendedInterface extends Interface {}

  static class ImplementationOfExtendedInterface implements ExtendedInterface {
    @Override
    public String plain() {
      return "hello!";
    }

    @Override
    public String named() {
      return "hello!";
    }

    @Override
    public String withAttributes(String value, String notTraced) {
      return value;
    }
  }

  abstract static class AbstractClass {
    @WithSpan("baseName")
    public abstract String run();

    @WithSpan(inheritContext = false)
    public abstract String detached();
  }

  static class ImplementationOfAbstractClass extends AbstractClass {
    @Override
    public String run() {
      return "hello!";
    }

    @Override
    public String detached() {
      return "hello!";
    }

    String runBoth() {
      return runWithDetached();
    }

    @WithSpan("baseName")
    String runWithDetached() {
      return detached();
    }
  }

  @Test
  void onlyTheOverloadThatOverridesTheAnnotatedMethodIsTraced() {
    ImplementationOfInterfaceWithOverloads service = new ImplementationOfInterfaceWithOverloads();
    service.overloaded("a");
    service.overloaded((Object) "b");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfInterfaceWithOverloads.overloaded")
                        .hasNoParent()));
  }

  @Test
  void unrelatedOverloadOfGenericInterfaceIsNotTraced() {
    ImplementationOfGenericInterfaceWithOverloads service =
        new ImplementationOfGenericInterfaceWithOverloads();
    service.handle(1);
    service.handle("a");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfGenericInterfaceWithOverloads.handle")
                        .hasNoParent()));
  }

  @Test
  void privateMethodWithSameSignatureIsNotTraced() {
    PrivateSub service = new PrivateSub();
    service.callSub();
    service.callBase();

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("PrivateBase.work")));
  }

  @Test
  void genericInterfaceOverriddenInSubclassOfImplementation() {
    new ExtendedImplementationOfGenericInterface().handle("hello");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ExtendedImplementationOfGenericInterface.handle")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ExtendedImplementationOfGenericInterface.class.getName()
                                    + ".handle"),
                            equalTo(stringKey("message"), "hello"))));
  }

  @Test
  void nonOverridingOverloadIsNotTreatedAsInheritingWithSpan() {
    new ImplementationWithNonOverridingOverload().parent();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationWithNonOverridingOverload.parent")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationWithNonOverridingOverload.class.getName()
                                    + ".parent"),
                            equalTo(stringKey("value"), "a"))));
  }

  @Test
  void overrideOfGenericMethodInheritedFromInnerClassOfParameterizedOuter() {
    new ImplementationOfInnerBase(new OuterWithGenericBase<>()).handle("hello");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfInnerBase.handle")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationOfInnerBase.class.getName() + ".handle"),
                            equalTo(stringKey("value"), "hello"))));
  }

  @Test
  void packagePrivateAnnotatedMethodOverriddenThroughPublicOverrideInOtherPackage() {
    new OverrideInOtherPackage().run();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("OverrideInOtherPackage.run").hasNoParent()));
  }

  @Test
  void packagePrivateOverrideInOtherPackageDoesNotInheritWithSpan() {
    new PublicOverrideOfPackagePrivateOverride().run();

    testing.waitAndAssertTraces();
  }

  @Test
  void unrelatedOverloadOfGenericMethodKeepsAddingSpanAttributes() {
    new ImplementationWithUnrelatedGenericOverload().parent();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationWithUnrelatedGenericOverload.parent")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationWithUnrelatedGenericOverload.class.getName()
                                    + ".parent"),
                            equalTo(longKey("value"), 1L))));
  }

  @Test
  void boundedGenericInterface() {
    new ImplementationOfBoundedGenericInterface().bounded(1);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfBoundedGenericInterface.bounded")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationOfBoundedGenericInterface.class.getName()
                                    + ".bounded"),
                            equalTo(longKey("number"), 1L))));
  }

  @Test
  void arrayGenericInterface() {
    new ImplementationOfArrayGenericInterface().array(new String[] {"a", "b"});

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("ImplementationOfArrayGenericInterface.array")));
  }

  @Test
  void covariantReturnType() {
    new ImplementationOfCovariantInterface().value();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("ImplementationOfCovariantInterface.value")));
  }

  @Test
  void repeatedCallsAreTraced() {
    ImplementationOfInterface service = new ImplementationOfInterface();
    service.withAttributes("a", "b");
    service.withAttributes("c", "d");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfInterface.withAttributes")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationOfInterface.class.getName() + ".withAttributes"),
                            equalTo(stringKey("value"), "a"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfInterface.withAttributes")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationOfInterface.class.getName() + ".withAttributes"),
                            equalTo(stringKey("value"), "c"))));
  }

  @Test
  void spanAttributeOnlyOnImplementation() {
    new ImplementationOfInterfaceWithAttributes().childAttribute("a");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfInterfaceWithAttributes.childAttribute")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationOfInterfaceWithAttributes.class.getName()
                                    + ".childAttribute"),
                            equalTo(stringKey("value"), "a"))));
  }

  @Test
  void spanAttributesSplitBetweenInterfaceAndImplementation() {
    new ImplementationOfInterfaceWithAttributes().split("a", "b");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfInterfaceWithAttributes.split")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationOfInterfaceWithAttributes.class.getName() + ".split"),
                            equalTo(stringKey("first"), "a"),
                            equalTo(stringKey("second"), "b"))));
  }

  @Test
  void spanAttributeOnImplementationWins() {
    new ImplementationOfInterfaceWithAttributes().overridden("a");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfInterfaceWithAttributes.overridden")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationOfInterfaceWithAttributes.class.getName()
                                    + ".overridden"),
                            equalTo(stringKey("child"), "a"))));
  }

  @Test
  void methodWithoutAnnotationInHierarchyIsNotTraced() {
    ImplementationOfInterfaceWithAttributes service = new ImplementationOfInterfaceWithAttributes();
    service.notTraced();
    service.childAttribute("a");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("ImplementationOfInterfaceWithAttributes.childAttribute")));
  }

  @Test
  void interfaceInheritedThroughOtherInterface() {
    new ImplementationOfExtendedInterface().named();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("interfaceName").hasKind(SpanKind.PRODUCER).hasNoParent()));
  }

  @Test
  void abstractClassMethod() {
    new ImplementationOfAbstractClass().run();

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("baseName").hasNoParent()));
  }

  @Test
  void inheritContextFalseFromSuperClass() {
    new ImplementationOfAbstractClass().runBoth();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("baseName", "ImplementationOfAbstractClass.detached"),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("baseName").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("ImplementationOfAbstractClass.detached").hasNoParent()));
  }

  @Test
  void interfaceMethodImplementedByClass() {
    new ImplementationOfInterface().named();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("interfaceName")
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationOfInterface.class.getName() + ".named"))));
  }

  @Test
  void interfaceMethodImplementedBySuperClass() {
    new ImplementationOfInterface().plain();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("AbstractImplementationOfInterface.plain")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()));
  }

  @Test
  void interfaceParameterAttributes() {
    new ImplementationOfInterface().withAttributes("a", "b");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfInterface.withAttributes")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationOfInterface.class.getName() + ".withAttributes"),
                            equalTo(stringKey("value"), "a"))));
  }

  @Test
  void genericInterface() {
    new ImplementationOfGenericInterface().handle("hi");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ImplementationOfGenericInterface.handle")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                ImplementationOfGenericInterface.class.getName() + ".handle"),
                            equalTo(stringKey("message"), "hi"))));
  }

  @Test
  void annotationOnImplementationWins() {
    new OverridingImplementationOfInterface().named();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("implName").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  void interfaceAnnotationWithImplementationWithoutAnnotation() {
    new OverridingImplementationOfInterface().plain();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("OverridingImplementationOfInterface.plain").hasNoParent()));
  }
}
