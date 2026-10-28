import java.security.MessageDigest

plugins {
  id("otel.java-conventions")
  id("com.google.osdetector")
}

// LINE bundles native compilers for Linux/macOS (x86-64 and ARM64) and Windows (x86-64).
// Pin both the distribution commit and each binary's SHA-256, independently of download metadata.
val thriftCompilerVersion = if (otelProps.testLatestDeps) "0.22" else "0.13"
val thriftCompilerChecksums = if (otelProps.testLatestDeps) {
  mapOf(
    "linux-x86_64" to "fad6bc70c5086fabbd3428a8f969be0c7e288e23181a3e3377fe85cc09c8e409",
    "linux-aarch_64" to "9d8eb4c8d3409de63350a6b5d371fcb4fe16ff0a73acbe6e0db61ce464cace8a",
    "osx-x86_64" to "595b9b4a4a7a00bd1bf4678009b45230c73d01d654a5dda5e5d03408be4af380",
    "osx-aarch_64" to "17fac019453b54017b9e510c3b72f44238730b4a82ab394d4abc21e2ec8c8855",
    "windows-x86_64.exe" to "0596d92a9093263ce1309de7911c6a7cbfebbd4af6c484b537a2432f5826e101"
  )
} else {
  mapOf(
    "linux-x86_64" to "4d1b88c51408826eeb59521f3589d7c1089931cf669b5a0a46c4ac38af277856",
    "linux-aarch_64" to "d7b2cd496a036d3857238effc7a8f953f7b5f8c1fc72b293fa106941679ff886",
    "osx-x86_64" to "c8034939ede284e822ec3dcde1c2f0a2da8795ef584476b5e2f99a4414590914",
    "osx-aarch_64" to "138b2c867e0978b30ba613921ab2eb9e7bc6849eb5a787c7010494fb1b53c151",
    "windows-x86_64.exe" to "92753ef860304eb6b3fdbbb3e07ae23b30827194d3c2a072bb4cf7a65be87693"
  )
}
val thriftCompilerPlatform = providers.provider {
  val platform = osdetector.classifier + if (osdetector.os == "windows") ".exe" else ""
  if (platform !in thriftCompilerChecksums) {
    throw GradleException("No native Thrift $thriftCompilerVersion compiler is available for $platform")
  }
  platform
}

repositories {
  mavenCentral()
  mavenLocal()
  exclusiveContent {
    forRepository {
      ivy {
        name = "thriftCompilers"
        url = uri("https://raw.githubusercontent.com/line/gradle-scripts/55f08e044a1100c63c814298a230df6b786009e2/lib/thrift")
        patternLayout {
          artifact("[revision]/thrift.[classifier]")
        }
        metadataSources {
          artifact()
        }
      }
    }
    filter {
      includeModule("com.linecorp.gradle-scripts", "thrift")
    }
  }
}

// Gradle's artifact cache keeps the compiler available for regeneration with --offline.
val thriftCompiler = configurations.create("thriftCompiler") {
  isCanBeConsumed = false
  isTransitive = false
  defaultDependencies {
    add(project.dependencies.create("com.linecorp.gradle-scripts:thrift:$thriftCompilerVersion:${thriftCompilerPlatform.get()}@exe"))
  }
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  val thriftVersion = baseVersion("0.13.0").orLatest()
  implementation("org.apache.thrift:libthrift:$thriftVersion")
}

val generatedThriftDir = layout.buildDirectory.dir("generated/thrift/main")

val generateThriftSources = tasks.register<Exec>("generateThriftSources") {
  val thriftFilePath =
    layout.projectDirectory.file("src/main/thrift/custom.thrift").asFile.absolutePath
  val outputDir = generatedThriftDir.get().asFile
  val outputDirPath = outputDir.absolutePath
  val compilerFiles = files(thriftCompiler)
  val compilerChecksum = thriftCompilerPlatform.map { thriftCompilerChecksums.getValue(it) }.get()
  val isWindows = osdetector.os == "windows"
  inputs.file(thriftFilePath)
  inputs.files(compilerFiles).withPropertyName("thriftCompiler").withPathSensitivity(PathSensitivity.NONE)
  inputs.property("thriftCompilerVersion", thriftCompilerVersion)
  inputs.property("thriftCompilerChecksum", compilerChecksum)
  outputs.dir(outputDirPath)

  standardOutput = System.out
  doFirst {
    // Do not change permissions on Gradle's shared artifact cache.
    val localCompiler = compilerFiles.singleFile.copyTo(temporaryDir.resolve("thrift.exe"), overwrite = true)
    val checksum = MessageDigest.getInstance("SHA-256").digest(localCompiler.readBytes())
      .joinToString("") { "%02x".format(it) }
    if (checksum != compilerChecksum) {
      throw GradleException("SHA-256 mismatch for native Thrift compiler: $localCompiler")
    }
    if (!isWindows && !localCompiler.setExecutable(true)) {
      throw GradleException("Cannot make Thrift compiler executable: $localCompiler")
    }
    executable = localCompiler.absolutePath
    outputDir.mkdirs()
  }
  args = listOf(
    "--gen",
    "java",
    "-out",
    outputDirPath,
    thriftFilePath
  )
}

sourceSets {
  main {
    java {
      srcDir(generatedThriftDir)
    }
  }
}

tasks.compileJava {
  dependsOn(generateThriftSources)
  // Generated Thrift sources contain doc comments that are not attached to declarations.
  options.compilerArgs.add("-Xlint:-dangling-doc-comments")
}

tasks.sourcesJar {
  dependsOn(generateThriftSources)
}

tasks.named<Checkstyle>("checkstyleMain") {
  exclude("**/custom/**")
}
