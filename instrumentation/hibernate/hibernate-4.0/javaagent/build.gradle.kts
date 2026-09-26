plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.hibernate")
    module.set("hibernate-core")
    versions.set("[4.0.0.Final,6)")
    assertInverse.set(true)
    excludeInstrumentationName("hibernate-4.0-procedure-call")
  }
  pass {
    name.set("Hibernate procedure calls")
    group.set("org.hibernate")
    module.set("hibernate-core")
    versions.set("[4.3.0.Final,)")
    assertInverse.set(true)
    excludeInstrumentationName("hibernate-4.0-core")
  }
  pass {
    name.set("Hibernate 6+ procedure calls")
    group.set("org.hibernate.orm")
    module.set("hibernate-core")
    versions.set("[6.0.0.Final,)")
    assertInverse.set(true)
    excludeInstrumentationName("hibernate-4.0-core")
  }
}

dependencies {
  compileOnly("org.hibernate:hibernate-core:4.3.0.Final")

  implementation(project(":instrumentation:hibernate:hibernate-common-3.3:javaagent"))

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  // Added to ensure cross compatibility:
  testInstrumentation(project(":instrumentation:hibernate:hibernate-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:hibernate:hibernate-6.0:javaagent"))

  testImplementation("com.h2database:h2:1.4.197")
  testImplementation("javax.xml.bind:jaxb-api:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-core:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-impl:2.2.11")
  testImplementation("javax.activation:activation:1.1.1")
  testImplementation("org.hsqldb:hsqldb:2.0.0")
  // First version to work with Java 14
  testImplementation("org.springframework.data:spring-data-jpa:1.8.0.RELEASE")

  testImplementation("org.hibernate:hibernate-core:4.0.0.Final")
  testImplementation("org.hibernate:hibernate-entitymanager:4.0.0.Final")
  testImplementation(project(":instrumentation:hibernate:testing"))

  testImplementation("org.javassist:javassist:3.28.0-GA")
}

testing {
  suites {
    register<JvmTestSuite>("procedureCallTest") {
      dependencies {
        val hibernateVersion = baseVersion("4.3.0.Final").orLatest("5.+")
        implementation("org.hibernate:hibernate-core:$hibernateVersion")
        implementation("org.hibernate:hibernate-entitymanager:$hibernateVersion")
        implementation("org.hsqldb:hsqldb:2.0.0")
        implementation("javax.xml.bind:jaxb-api:2.3.1")
        implementation("org.glassfish.jaxb:jaxb-runtime:2.3.3")
        implementation(project(":instrumentation:hibernate:testing"))
      }
    }
    register<JvmTestSuite>("version5Test") {
      targets.all {
        testTask.configure {
          jvmArgs("-Dotel.instrumentation.hibernate.experimental-span-attributes=true")
          systemProperty("metadataConfig", "otel.instrumentation.hibernate.experimental-span-attributes=true")
        }
      }
      dependencies {
        sources {
          java {
            setSrcDirs(listOf("src/test/java"))
          }
          resources {
            setSrcDirs(listOf("src/test/resources"))
          }
        }

        implementation("com.h2database:h2:1.4.197")
        implementation("javax.xml.bind:jaxb-api:2.2.11")
        implementation("com.sun.xml.bind:jaxb-core:2.2.11")
        implementation("com.sun.xml.bind:jaxb-impl:2.2.11")
        implementation("javax.activation:activation:1.1.1")
        implementation("org.hsqldb:hsqldb:2.0.0")
        implementation(project(":instrumentation:hibernate:testing"))

        val hibernateVersion = baseVersion("5.+").orLatest("5.0.0.Final")
        implementation("org.hibernate:hibernate-core:$hibernateVersion")
        implementation("org.hibernate:hibernate-entitymanager:$hibernateVersion")
        implementation("org.springframework.data:spring-data-jpa:${baseVersion("(2.4.0,3)").orLatest("2.3.0.RELEASE")}")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.hibernate.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.hibernate.experimental-span-attributes=true")
  }

  val testDisabled = register<Test>("testDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*DefaultEnablementTest")
    }

    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.instrumentation.jdbc.enabled=false")
    systemProperty("collectMetadata", false)
  }

  val procedureCallSuite = testing.suites.named<JvmTestSuite>("procedureCallTest")
  val procedureCallTestExperimental = register<Test>("procedureCallTestExperimental") {
    testClassesDirs = procedureCallSuite.get().sources.output.classesDirs
    classpath = procedureCallSuite.get().sources.runtimeClasspath

    jvmArgs("-Dotel.instrumentation.hibernate.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.hibernate.experimental-span-attributes=true")
  }

  val procedureCallTestDisabled = register<Test>("procedureCallTestDisabled") {
    testClassesDirs = procedureCallSuite.get().sources.output.classesDirs
    classpath = procedureCallSuite.get().sources.runtimeClasspath
    filter {
      includeTestsMatching("*DefaultEnablementTest")
    }

    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.instrumentation.jdbc.enabled=false")
    systemProperty("collectMetadata", false)
  }

  val aliasTests = mapOf(
    "SharedAliasDisabled" to mapOf("hibernate" to false),
    "OwnerAliasDisabled" to mapOf("hibernate-4.0" to false),
    "CoreAliasDisabled" to mapOf("hibernate-4.0-core" to false),
    "ProcedureAliasDisabled" to mapOf("hibernate-4.0-procedure-call" to false),
    "LegacyProcedureAliasDisabled" to mapOf("hibernate-procedure-call" to false),
    "LegacyProcedureVersionAliasDisabled" to mapOf("hibernate-procedure-call-4.3" to false),
    "AliasPrecedence" to mapOf("hibernate" to true, "hibernate-4.0" to false, "hibernate-procedure-call" to false, "hibernate-procedure-call-4.3" to false),
    "OwnerPrecedence" to mapOf("hibernate-4.0" to true, "hibernate-procedure-call" to false, "hibernate-procedure-call-4.3" to false),
    "ProcedureAliasOverride" to mapOf("hibernate-4.0-core" to false, "hibernate-procedure-call-4.3" to true),
    "PreviewLegacyIgnored" to mapOf("hibernate-procedure-call" to true, "hibernate-procedure-call-4.3" to true),
    "PreviewNewAliasEnabled" to mapOf("hibernate-4.0" to true, "hibernate-procedure-call" to false, "hibernate-procedure-call-4.3" to false),
  ).map { (name, aliases) ->
    register<Test>("procedureCallTest$name") {
      testClassesDirs = procedureCallSuite.get().sources.output.classesDirs
      classpath = procedureCallSuite.get().sources.runtimeClasspath
      filter {
        includeTestsMatching("*DefaultEnablementTest")
      }
      aliases.forEach { (alias, enabled) ->
        jvmArgs("-Dotel.instrumentation.$alias.enabled=$enabled")
      }
      if (name.startsWith("Preview")) {
        jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
      }
      jvmArgs("-Dotel.instrumentation.jdbc.enabled=false")
      systemProperty("testEnablement", true)
      systemProperty("testCoreEnabled", name in listOf("ProcedureAliasDisabled", "LegacyProcedureAliasDisabled", "LegacyProcedureVersionAliasDisabled", "AliasPrecedence", "OwnerPrecedence", "PreviewNewAliasEnabled"))
      systemProperty("testProcedureEnabled", name in listOf("CoreAliasDisabled", "AliasPrecedence", "OwnerPrecedence", "ProcedureAliasOverride", "PreviewNewAliasEnabled"))
      systemProperty("collectMetadata", false)
    }
  }

  val stableSemconvSuites = testing.suites.withType(JvmTestSuite::class)
    .map { suite ->
      register<Test>("${suite.name}StableSemconv") {
        val sourceTask = named<Test>(suite.name).get()
        setJvmArgs(sourceTask.jvmArgs)
        setSystemProperties(sourceTask.systemProperties)
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        jvmArgs("-Dotel.semconv-stability.opt-in=database")
        systemProperty(
          "metadataConfig",
          listOfNotNull(sourceTask.systemProperties["metadataConfig"], "otel.semconv-stability.opt-in=database")
            .joinToString(","),
        )
      }
    }

  check {
    dependsOn(testing.suites, testDisabled, testExperimental, stableSemconvSuites, procedureCallTestExperimental, procedureCallTestDisabled, aliasTests)
  }
}
