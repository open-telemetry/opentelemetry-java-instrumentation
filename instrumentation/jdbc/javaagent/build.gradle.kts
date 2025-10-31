plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    coreJdk()
  }
}

dependencies {
  bootstrap(project(":instrumentation:jdbc:bootstrap"))
  compileOnly(
    project(
      path = ":instrumentation:jdbc:library",
      configuration = "shadow",
    ),
  )
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("com.h2database:h2:1.3.169")
  testLibrary("org.apache.derby:derby:10.6.1.0")
  testLibrary("org.hsqldb:hsqldb:2.0.0")

  testLibrary("org.apache.tomcat:tomcat-jdbc:7.0.19")
  testLibrary("org.apache.tomcat:tomcat-juli:7.0.19") // tomcat jdbc needs this
  testLibrary("com.zaxxer:HikariCP:2.4.0")
  testLibrary("com.mchange:c3p0:0.9.5")
  testLibrary("com.alibaba:druid:1.2.20")

  // some classes in earlier versions of derby were split out into derbytools in later versions
  latestDepTestLibrary("org.apache.derby:derbytools:latest.release")

  testImplementation("com.google.guava:guava")
  testImplementation(project(":instrumentation:jdbc:testing"))

  // these dependencies are for SlickTest
  testImplementation("org.scala-lang:scala-library:2.11.12")
  testImplementation("com.typesafe.slick:slick_2.11:3.2.0")
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:jdbc:library")
    output.dir(
      shadedDep.file("build/extracted/shadow-javaagent"),
      "builtBy" to ":instrumentation:jdbc:library:extractShadowJarJavaagent",
    )
  }
}

tasks {
  val testSlick by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("SlickTest")
    }
    include("**/SlickTest.*")
  }

  val testSqlCommenter by registering(Test::class) {
    filter {
      includeTestsMatching("SqlCommenterTest")
    }
    include("**/SqlCommenterTest.*")
    jvmArgs("-Dotel.instrumentation.jdbc.experimental.sqlcommenter.enabled=true")
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("SlickTest")
      excludeTestsMatching("SqlCommenterTest")
      excludeTestsMatching("PreparedStatementParametersTest")
    }
    jvmArgs("-Dotel.instrumentation.jdbc-datasource.enabled=true")
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  val testSlickStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("SlickTest")
    }
    include("**/SlickTest.*")
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  val testCaptureParameters by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("PreparedStatementParametersTest")
    }
    jvmArgs("-Dotel.instrumentation.jdbc.experimental.capture-query-parameters=true")
  }

  test {
    filter {
      excludeTestsMatching("SlickTest")
      excludeTestsMatching("SqlCommenterTest")
      excludeTestsMatching("PreparedStatementParametersTest")
    }
    jvmArgs("-Dotel.instrumentation.jdbc-datasource.enabled=true")
  }

  check {
    dependsOn(testSlick)
    dependsOn(testSqlCommenter)
    dependsOn(testStableSemconv)
    dependsOn(testSlickStableSemconv)
    dependsOn(testCaptureParameters)
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    jvmArgs("-Dotel.instrumentation.jdbc.experimental.transaction.enabled=true")
  }
}
