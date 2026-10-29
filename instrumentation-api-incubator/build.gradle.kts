import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.osgi-conventions")
  id("otel.publish-conventions")
  id("otel.nullaway-conventions")
}

group = "io.opentelemetry.instrumentation"

val jflex = configurations.create("jflex")

dependencies {
  jflex("de.jflex:jflex:1.9.1")

  api("io.opentelemetry.semconv:opentelemetry-semconv")
  api(project(":instrumentation-api"))
  api("io.opentelemetry:opentelemetry-api-incubator")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")
  testImplementation("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry.semconv:opentelemetry-semconv-incubating")
}

val jflexSourceDir = layout.projectDirectory.dir("src/main/jflex")
val jflexOutputDir = layout.buildDirectory.dir("generated/sources/jflex")

val generateJflex = tasks.register<JavaExec>("generateJflex") {
  classpath(jflex)
  mainClass.set("jflex.Main")

  inputs.dir(jflexSourceDir)
  outputs.dir(jflexOutputDir)

  val sourceDir = jflexSourceDir
  val outputDirProvider = jflexOutputDir

  doFirst {
    val outputDir = outputDirProvider.get().asFile
    check(outputDir.deleteRecursively()) {
      "Failed to clear generated JFlex sources: $outputDir"
    }
    outputDir.mkdirs()
    val specFiles = listOf(
      sourceDir.asFile.resolve("SqlSanitizer.jflex"),
    )
    args(
      listOf("-d", outputDir.absolutePath, "--nobak") + specFiles.map { it.absolutePath },
    )
  }
}

sourceSets {
  main {
    java.srcDir(jflexOutputDir)
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
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          inputs.dir(jflexOutputDir)
        }
        register("PreviewSemconv") {
          jvmArgs("-Dotel.semconv-stability.preview=service.peer")
        }
        register("BothSemconv") {
          jvmArgs("-Dotel.semconv-stability.preview=service.peer/dup")
        }
        register("ExceptionSignalLogs") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
        }
        register("ExceptionSignalLogsDup") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs/dup")
        }
      }
    }
  }
}
