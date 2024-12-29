plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.google.gwt")
    module.set("gwt-servlet")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
  }
  // GWT changed group name in 2.10.0
  pass {
    group.set("org.gwtproject")
    module.set("gwt-servlet")
    versions.set("[2.10.0,)")
    assertInverse.set(true)
  }
}

sourceSets {
  create("testapp") {
    java {
      destinationDirectory.set(layout.buildDirectory.dir("testapp/classes"))
    }
    resources {
      srcDirs("src/webapp")
    }
    compileClasspath = compileClasspath.plus(sourceSets.main.get().compileClasspath)
  }
}

dependencies {
  // these are needed for compileGwt task
  if (findProperty("testLatestDeps") as Boolean) {
    compileOnly("org.gwtproject:gwt-user:+")
    compileOnly("org.gwtproject:gwt-dev:+")
    compileOnly("org.gwtproject:gwt-servlet:+")
    testImplementation("org.gwtproject:gwt-servlet:+")
  } else {
    compileOnly("com.google.gwt:gwt-user:2.0.0")
    compileOnly("com.google.gwt:gwt-dev:2.0.0")
    compileOnly("com.google.gwt:gwt-servlet:2.0.0")
    testImplementation("com.google.gwt:gwt-servlet:2.0.0")
  }

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  testImplementation("org.testcontainers:selenium")
  testImplementation("org.seleniumhq.selenium:selenium-java:4.8.3")

  testImplementation("org.eclipse.jetty:jetty-webapp:9.4.35.v20201120")
}

val warDir = layout.buildDirectory.dir("testapp/war")

val launcher = javaToolchains.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(8))
}

class CompilerArgumentsProvider : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> = listOf(
    // gwt module
    "test.gwt.Greeting",
    "-war", layout.buildDirectory.dir("testapp/war").get().asFile.absolutePath,
    "-logLevel", "INFO",
    "-localWorkers", "2",
    "-compileReport",
    "-extra", layout.buildDirectory.dir("testapp/extra").get().asFile.absolutePath,
    // makes compile a bit faster
    "-draftCompile",
  )
}

tasks {
  val compileGwt by registering(JavaExec::class) {
    dependsOn(classes)
    // versions before 2.9 require java8
    javaLauncher.set(launcher)

    outputs.cacheIf { true }
    outputs.dir(warDir)

    mainClass.set("com.google.gwt.dev.Compiler")

    classpath(sourceSets["testapp"].java.srcDirs, sourceSets["testapp"].compileClasspath)

    argumentProviders.add(CompilerArgumentsProvider())

    if (findProperty("testLatestDeps") as Boolean) {
      javaLauncher.set(project.javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(11)
      })
    }
  }

  val copyTestWebapp by registering(Copy::class) {
    dependsOn(compileGwt)

    from(file("src/testapp/webapp"))
    from(warDir)

    into(file(layout.buildDirectory.dir("testapp/web")))
  }

  test {
    dependsOn(sourceSets["testapp"].output)
    dependsOn(copyTestWebapp)

    // add test app classes to classpath
    classpath = sourceSets.test.get().runtimeClasspath.plus(files(layout.buildDirectory.dir("testapp/classes")))

    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
