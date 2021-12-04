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
}

sourceSets {
  create("testapp") {
    java {
      destinationDirectory.set(file("$buildDir/testapp/classes"))
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
    compileOnly("com.google.gwt:gwt-user:+")
    compileOnly("com.google.gwt:gwt-dev:+")
  } else {
    compileOnly("com.google.gwt:gwt-user:2.0.0")
    compileOnly("com.google.gwt:gwt-dev:2.0.0")
  }

  library("com.google.gwt:gwt-servlet:2.0.0")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  testImplementation("org.testcontainers:selenium")
  testImplementation("org.seleniumhq.selenium:selenium-java:3.141.59")

  testImplementation("org.eclipse.jetty:jetty-webapp:9.4.35.v20201120")
}

val warDir = file("$buildDir/testapp/war")

val launcher = javaToolchains.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(8))
}

class CompilerArgumentsProvider : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> = listOf(
    "test.gwt.Greeting", // gwt module
    "-war", "$buildDir/testapp/war",
    "-logLevel", "INFO",
    "-localWorkers", "2",
    "-compileReport",
    "-extra", "$buildDir/testapp/extra",
    "-draftCompile" // makes compile a bit faster
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
  }

  val copyTestWebapp by registering(Copy::class) {
    dependsOn(compileGwt)

    from(file("src/testapp/webapp"))
    from(warDir)

    into(file("$buildDir/testapp/web"))
  }

  test {
    dependsOn(sourceSets["testapp"].output)
    dependsOn(copyTestWebapp)

    // add test app classes to classpath
    classpath = sourceSets.test.get().runtimeClasspath.plus(files("$buildDir/testapp/classes"))

    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
