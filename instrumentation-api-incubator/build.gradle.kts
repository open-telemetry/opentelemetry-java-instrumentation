plugins {
  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.publish-conventions")
  id("otel.nullaway-conventions")
}

group = "io.opentelemetry.instrumentation"

// JFlex configuration - manual integration for configuration cache compatibility
configurations {
  val jflex by creating {
    isTransitive = true
  }
}

dependencies {
  "jflex"("de.jflex:jflex:1.9.1")
  "jflex"("com.github.vbmacher:java-cup-runtime:11b-20160615")

  api("io.opentelemetry.semconv:opentelemetry-semconv")
  api(project(":instrumentation-api"))
  api("io.opentelemetry:opentelemetry-api-incubator")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry.semconv:opentelemetry-semconv-incubating")
}

// Manual JFlex task - configuration cache compatible
val generateJflex by tasks.registering(JavaExec::class) {
  description = "Generate Java code from JFlex files"
  group = "build"

  classpath = configurations.getByName("jflex")
  mainClass.set("jflex.Main")

  val jflexSourceDir = file("src/main/jflex")
  val jflexOutputDir = file("build/generated/sources/jflex")

  inputs.dir(jflexSourceDir)
  outputs.dir(jflexOutputDir)

  doFirst {
    jflexOutputDir.mkdirs()
  }

  args(
    "-d", jflexOutputDir,
    "--nobak",
    "$jflexSourceDir/SqlSanitizer.jflex"
  )
}

sourceSets {
  main {
    java.srcDir(generateJflex.map { it.outputs.files.singleFile })
  }
}

tasks.compileJava {
  dependsOn(generateJflex)
}

tasks {
  // exclude auto-generated code
  named<Checkstyle>("checkstyleMain") {
    exclude("**/AutoSqlSanitizer.java")
  }

  // Work around https://github.com/jflex-de/jflex/issues/762
  compileJava {
    with(options) {
      compilerArgs.add("-Xlint:-fallthrough")
    }
  }

  sourcesJar {
    dependsOn(generateJflex)
    // Avoid configuration cache issue by not capturing task reference
    from("src/main/jflex") {
      include("**/*.java")
    }
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database,code")
  }

  val testBothSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database/dup,code/dup")
  }

  check {
    dependsOn(testStableSemconv, testBothSemconv)
  }
}
