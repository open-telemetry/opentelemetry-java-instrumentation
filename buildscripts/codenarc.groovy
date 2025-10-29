ruleset {
  // rulesets/basic.xml
  /*
  AssertWithinFinallyBlock
  AssignmentInConditional
  BigDecimalInstantiation
  BitwiseOperatorInConditional
  BooleanGetBoolean
  BrokenNullCheck
  BrokenOddnessCheck
  ClassForName
  ComparisonOfTwoConstants
  ComparisonWithSelf
  ConstantAssertExpression
  ConstantIfExpression
  ConstantTernaryExpression
  DeadCode
  DoubleNegative
  DuplicateCaseStatement
  DuplicateMapKey
  DuplicateSetValue
  EmptyCatchBlock
  EmptyClass
  EmptyElseBlock
  EmptyFinallyBlock
  EmptyForStatement
  EmptyIfStatement
  EmptyInstanceInitializer
  EmptyMethod
  EmptyStaticInitializer
  EmptySwitchStatement
  EmptySynchronizedStatement
  EmptyTryBlock
  EmptyWhileStatement
  EqualsAndHashCode
  EqualsOverloaded
  ExplicitGarbageCollection
  ForLoopShouldBeWhileLoop
  HardCodedWindowsFileSeparator
  HardCodedWindowsRootDirectory
  IntegerGetInteger
  RandomDoubleCoercedToZero
  RemoveAllOnSelf
  ReturnFromFinallyBlock
  ThrowExceptionFromFinallyBlock
  */

  // rulesets/braces.xml
  ElseBlockBraces
  ForStatementBraces
  IfStatementBraces
  WhileStatementBraces

  // rulesets/concurrency.xml
  /*
  BusyWait
  DoubleCheckedLocking
  InconsistentPropertyLocking
  InconsistentPropertySynchronization
  NestedSynchronization
  StaticCalendarField
  StaticConnection
  StaticDateFormatField
  StaticMatcherField
  StaticSimpleDateFormatField
  SynchronizedMethod
  SynchronizedOnBoxedPrimitive
  SynchronizedOnGetClass
  SynchronizedOnReentrantLock
  SynchronizedOnString
  SynchronizedOnThis
  SynchronizedReadObjectMethod
  SystemRunFinalizersOnExit
  ThisReferenceEscapesConstructor
  ThreadGroup
  ThreadLocalNotStaticFinal
  ThreadYield
  UseOfNotifyMethod
  VolatileArrayField
  VolatileLongOrDoubleField
  WaitOutsideOfWhileLoop
  */

  // rulesets/convention.xml
  /*
  ConfusingTernary
  CouldBeElvis
  HashtableIsObsolete
  IfStatementCouldBeTernary
  InvertedIfElse
  LongLiteralWithLowerCaseL
  ParameterReassignment
  TernaryCouldBeElvis
  VectorIsObsolete
  */

  // rulesets/design.xml
  /*
  AbstractClassWithPublicConstructor
  AbstractClassWithoutAbstractMethod
  BooleanMethodReturnsNull
  BuilderMethodWithSideEffects
  CloneableWithoutClone
  CloseWithoutCloseable
  CompareToWithoutComparable
  ConstantsOnlyInterface
  EmptyMethodInAbstractClass
  FinalClassWithProtectedMember
  ImplementationAsType
  LocaleSetDefault
  PrivateFieldCouldBeFinal
  PublicInstanceField
  ReturnsNullInsteadOfEmptyArray
  ReturnsNullInsteadOfEmptyCollection
  SimpleDateFormatMissingLocale
  StatelessSingleton
  */

  // rulesets/dry.xml
  /*
  DuplicateListLiteral
  DuplicateMapLiteral
  DuplicateNumberLiteral
  DuplicateStringLiteral
  */

  // rulesets/enhanced.xml
  /*
  CloneWithoutCloneable
  JUnitAssertEqualsConstantActualValue
  UnsafeImplementationAsMap
  */

  // rulesets/exceptions.xml
  /*
  CatchArrayIndexOutOfBoundsException
  CatchError
  CatchException
  CatchIllegalMonitorStateException
  CatchIndexOutOfBoundsException
  CatchNullPointerException
  CatchRuntimeException
  CatchThrowable
  ConfusingClassNamedException
  ExceptionExtendsError
  ExceptionNotThrown
  MissingNewInThrowStatement
  ReturnNullFromCatchBlock
  SwallowThreadDeath
  ThrowError
  ThrowException
  ThrowNullPointerException
  ThrowRuntimeException
  ThrowThrowable
  */

  // rulesets/formatting.xml
  /*
  BracesForClass
  BracesForForLoop
  BracesForIfElse
  BracesForMethod
  BracesForTryCatchFinally
  ClassJavadoc
  ClosureStatementOnOpeningLineOfMultipleLineClosure
  LineLength
  SpaceAfterCatch
  SpaceAfterClosingBrace
  SpaceAfterComma
  SpaceAfterFor
  SpaceAfterIf
  SpaceAfterOpeningBrace
  SpaceAfterSemicolon
  SpaceAfterSwitch
  SpaceAfterWhile
  SpaceAroundClosureArrow
  SpaceAroundMapEntryColon
  SpaceAroundOperator
  SpaceBeforeClosingBrace
  SpaceBeforeOpeningBrace
  */

  // rulesets/generic.xml
  /*
  IllegalClassMember
  IllegalClassReference
  IllegalPackageReference
  IllegalRegex
  IllegalString
  RequiredRegex
  RequiredString
  StatelessClass
  */

  // rulesets/grails.xml
  /*
  GrailsDomainHasEquals
  GrailsDomainHasToString
  GrailsDomainReservedSqlKeywordName
  GrailsDomainWithServiceReference
  GrailsDuplicateConstraint
  GrailsDuplicateMapping
  GrailsPublicControllerMethod
  GrailsServletContextReference
  GrailsSessionReference   // DEPRECATED
  GrailsStatelessService
  */

  // rulesets/groovyism.xml
  /*
  AssignCollectionSort
  AssignCollectionUnique
  ClosureAsLastMethodParameter
  CollectAllIsDeprecated
  ConfusingMultipleReturns
  ExplicitArrayListInstantiation
  ExplicitCallToAndMethod
  ExplicitCallToCompareToMethod
  ExplicitCallToDivMethod
  ExplicitCallToEqualsMethod
  ExplicitCallToGetAtMethod
  ExplicitCallToLeftShiftMethod
  ExplicitCallToMinusMethod
  ExplicitCallToModMethod
  ExplicitCallToMultiplyMethod
  ExplicitCallToOrMethod
  ExplicitCallToPlusMethod
  ExplicitCallToPowerMethod
  ExplicitCallToRightShiftMethod
  ExplicitCallToXorMethod
  ExplicitHashMapInstantiation
  ExplicitHashSetInstantiation
  ExplicitLinkedHashMapInstantiation
  ExplicitLinkedListInstantiation
  ExplicitStackInstantiation
  ExplicitTreeSetInstantiation
  GStringAsMapKey
  GStringExpressionWithinString
  GetterMethodCouldBeProperty
  GroovyLangImmutable
  UseCollectMany
  UseCollectNested
  */

  // rulesets/imports.xml
  DuplicateImport
  ImportFromSamePackage
//  ImportFromSunPackages
//  MisorderedStaticImports
  UnnecessaryGroovyImport
  UnusedImport

  // rulesets/jdbc.xml
  /*
  DirectConnectionManagement
  JdbcConnectionReference
  JdbcResultSetReference
  JdbcStatementReference
  */

  // rulesets/junit.xml
  /*
  ChainedTest
  CoupledTestCase
  JUnitAssertAlwaysFails
  JUnitAssertAlwaysSucceeds
  JUnitFailWithoutMessage
  JUnitLostTest
  JUnitPublicField
  JUnitPublicNonTestMethod
  JUnitSetUpCallsSuper
  JUnitStyleAssertions
  JUnitTearDownCallsSuper
  JUnitTestMethodWithoutAssert
  JUnitUnnecessarySetUp
  JUnitUnnecessaryTearDown
  JUnitUnnecessaryThrowsException
  SpockIgnoreRestUsed
  UnnecessaryFail
  UseAssertEqualsInsteadOfAssertTrue
  UseAssertFalseInsteadOfNegation
  UseAssertNullInsteadOfAssertEquals
  UseAssertSameInsteadOfAssertTrue
  UseAssertTrueInsteadOfAssertEquals
  UseAssertTrueInsteadOfNegation
  */

  // rulesets/logging.xml
  /*
  LoggerForDifferentClass
  LoggerWithWrongModifiers
  LoggingSwallowsStacktrace
  MultipleLoggers
  PrintStackTrace
  Println
  SystemErrPrint
  SystemOutPrint
  */

  // rulesets/naming.xml
  AbstractClassName
  ClassName {
    regex = '^[A-Z][\\$a-zA-Z0-9]*$'
  }
  ClassNameSameAsFilename
//  ConfusingMethodName
//  FactoryMethodName
  FieldName {
    regex = '^_?[a-z][a-zA-Z0-9]*$'
    finalRegex = '^_?[a-z][a-zA-Z0-9]*$'
    // can be either constant (ABC_XYZ) or non-constant (abcXyz)
    staticFinalRegex = '^[A-Z][A-Z_0-9]*$|^_?[a-z][a-zA-Z0-9]*$'
  }
  InterfaceName
  MethodName {
    regex = '^[a-z][\\$_a-zA-Z0-9]*$|^.*\\s.*$'
  }
  ObjectOverrideMisspelledMethodName
  ParameterName
  PropertyName
  VariableName {
    finalRegex = '^[a-z][a-zA-Z0-9]*$'
  }

  // rulesets/security.xml
  /*
  FileCreateTempFile
  InsecureRandom
  JavaIoPackageAccess
  NonFinalPublicField
  NonFinalSubclassOfSensitiveInterface
  ObjectFinalize
  PublicFinalizeMethod
  SystemExit
  UnsafeArrayDeclaration
  */

  // rulesets/serialization.xml
  /*
  EnumCustomSerializationIgnored
  SerialPersistentFields
  SerialVersionUID
  SerializableClassMustDefineSerialVersionUID
  */

  // rulesets/size.xml
  /*
  AbcComplexity   // DEPRECATED: Use the AbcMetric rule instead. Requires the GMetrics jar
  AbcMetric   // Requires the GMetrics jar
  ClassSize
  CrapMetric   // Requires the GMetrics jar and a Cobertura coverage file
  CyclomaticComplexity   // Requires the GMetrics jar
  MethodCount
  MethodSize
  NestedBlockDepth
  */

  // rulesets/unnecessary.xml
  AddEmptyString
  ConsecutiveLiteralAppends
  ConsecutiveStringConcatenation
  UnnecessaryBigDecimalInstantiation
  UnnecessaryBigIntegerInstantiation
  UnnecessaryBooleanExpression
  UnnecessaryBooleanInstantiation
//  UnnecessaryCallForLastElement
  UnnecessaryCallToSubstring
  UnnecessaryCatchBlock
//  UnnecessaryCollectCall
  UnnecessaryCollectionCall
  UnnecessaryConstructor
  UnnecessaryDefInFieldDeclaration
  UnnecessaryDefInMethodDeclaration
  UnnecessaryDefInVariableDeclaration
  UnnecessaryDotClass
  UnnecessaryDoubleInstantiation
  UnnecessaryElseStatement
  UnnecessaryFinalOnPrivateMethod
  UnnecessaryFloatInstantiation
//  UnnecessaryGString
//  UnnecessaryGetter
  UnnecessaryIfStatement
  UnnecessaryInstanceOfCheck
  UnnecessaryInstantiationToGetClass
  UnnecessaryIntegerInstantiation
  UnnecessaryLongInstantiation
  UnnecessaryModOne
  UnnecessaryNullCheck
  UnnecessaryNullCheckBeforeInstanceOf
//  UnnecessaryObjectReferences
  UnnecessaryOverridingMethod
//  UnnecessaryPackageReference
  UnnecessaryParenthesesForMethodCallWithClosure
  UnnecessaryPublicModifier
//  UnnecessaryReturnKeyword
//  UnnecessarySelfAssignment
  UnnecessarySemicolon
  UnnecessaryStringInstantiation
//  UnnecessarySubstring
  UnnecessaryTernaryExpression
  UnnecessaryTransientModifier

  // rulesets/unused.xml
  UnusedArray
//  UnusedMethodParameter
  UnusedObject
  UnusedPrivateField
  UnusedPrivateMethod
  UnusedPrivateMethodParameter
  UnusedVariable
}
