import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import org.apache.commons.lang.StringUtils

plugins {
  id("otel.spotless-conventions")

  id("com.bmuschko.docker-remote-api")
}

data class ImageTarget(
  val version: List<String>,
  val vm: List<String>,
  val jdk: List<String>,
  val args: Map<String, String> = emptyMap(),
  val war: String = "servlet-3.0",
  val windows: Boolean = true
)

val extraTag = findProperty("extraTag")
  ?: java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(java.time.LocalDateTime.now())

// Each line under appserver describes one matrix of (version x vm x jdk), dockerfile key overrides
// Dockerfile name, args key passes raw arguments to docker build
val targets = mapOf(
  "jetty" to listOf(
    ImageTarget(listOf("9.4.53"), listOf("hotspot", "openj9"), listOf("8", "11", "17", "21", "23"), mapOf("sourceVersion" to "9.4.53.v20231009")),
    ImageTarget(listOf("10.0.19"), listOf("hotspot", "openj9"), listOf("11", "17", "21", "23"), mapOf("sourceVersion" to "10.0.19")),
    ImageTarget(listOf("11.0.19"), listOf("hotspot", "openj9"), listOf("11", "17", "21", "23"), mapOf("sourceVersion" to "11.0.19"), "servlet-5.0"),
    ImageTarget(listOf("12.0.6"), listOf("hotspot", "openj9"), listOf("17", "21", "23"), mapOf("sourceVersion" to "12.0.6"), "servlet-5.0"),
  ),
  "liberty" to listOf(
    ImageTarget(listOf("20.0.0.12"), listOf("hotspot", "openj9"), listOf("8", "11"), mapOf("release" to "2020-11-11_0736")),
    ImageTarget(listOf("21.0.0.12"), listOf("hotspot", "openj9"), listOf("8", "11", "17"), mapOf("release" to "2021-11-17_1256")),
    // Java 19 is not supported until 22.0.0.10
    ImageTarget(listOf("22.0.0.12"), listOf("hotspot", "openj9"), listOf("8", "11", "17"), mapOf("release" to "22.0.0.12")),
    // Java 21 is not supported until 23.0.0.3 - despite that only 20 seems to work
    ImageTarget(listOf("23.0.0.12"), listOf("hotspot", "openj9"), listOf("8", "11", "17", "20"), mapOf("release" to "23.0.0.12")),
  ),
  "payara" to listOf(
    ImageTarget(listOf("5.2020.6", "5.2021.8"), listOf("hotspot", "openj9"), listOf("8", "11")),
    // Test application is not deployed when server is sarted with hotspot jdk version 21
    ImageTarget(listOf("6.2023.12"), listOf("hotspot"), listOf("11", "17"), war = "servlet-5.0"),
    ImageTarget(listOf("6.2023.12"), listOf("openj9"), listOf("11", "17", "21", "23"), war = "servlet-5.0")
  ),
  "tomcat" to listOf(
    ImageTarget(listOf("7.0.109"), listOf("hotspot", "openj9"), listOf("8"), mapOf("majorVersion" to "7")),
    ImageTarget(listOf("8.5.98"), listOf("hotspot", "openj9"), listOf("8", "11", "17", "21", "23"), mapOf("majorVersion" to "8")),
    ImageTarget(listOf("9.0.85"), listOf("hotspot", "openj9"), listOf("8", "11", "17", "21", "23"), mapOf("majorVersion" to "9")),
    ImageTarget(listOf("10.1.18"), listOf("hotspot", "openj9"), listOf("11", "17", "21", "23"), mapOf("majorVersion" to "10"), "servlet-5.0"),
  ),
  "tomee" to listOf(
    ImageTarget(listOf("7.0.9", "7.1.4"), listOf("hotspot", "openj9"), listOf("8")),
    ImageTarget(listOf("8.0.16"), listOf("hotspot", "openj9"), listOf("8", "11", "17", "21", "23")),
    ImageTarget(listOf("9.1.2"), listOf("hotspot", "openj9"), listOf("11", "17", "21", "23"), war = "servlet-5.0"),
  ),
  "websphere" to listOf(
    ImageTarget(listOf("8.5.5.22", "9.0.5.14"), listOf("openj9"), listOf("8"), windows = false),
  ),
  "wildfly" to listOf(
    ImageTarget(listOf("13.0.0.Final"), listOf("hotspot", "openj9"), listOf("8")),
    ImageTarget(
      listOf("17.0.1.Final", "21.0.0.Final"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21", "23")
    ),
    ImageTarget(
      listOf("28.0.1.Final", "29.0.1.Final", "30.0.1.Final"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21", "23"),
      war = "servlet-5.0"
    ),
  ),
)

val matrix = mutableListOf<String>()

tasks {
  val buildLinuxTestImages by registering {
    group = "build"
    description = "Builds all Linux Docker images for the test matrix"
  }

  val buildWindowsTestImages by registering {
    group = "build"
    description = "Builds all Windows Docker images for the test matrix"
  }

  val pushMatrix by registering(DockerPushImage::class) {
    mustRunAfter(buildLinuxTestImages)
    mustRunAfter(buildWindowsTestImages)
    group = "publishing"
    description = "Push all Docker images for the test matrix"
    images.set(matrix)
  }

  createDockerTasks(buildLinuxTestImages, false)
  createDockerTasks(buildWindowsTestImages, true)

  val printSmokeTestsConfigurations by registering {
    for ((server, matrices) in targets) {
      val smokeTestServer = findProperty("smokeTestServer")
      if (smokeTestServer != null && server != smokeTestServer) {
        continue
      }
      println(server)
      val serverName = StringUtils.capitalize(server)
      for (entry in matrices) {
        for (version in entry.version) {
          val dotIndex = version.indexOf('.')
          val majorVersion = if (dotIndex != -1) version.substring(0, dotIndex) else version
          for (jdk in entry.jdk) {
            for (vm in entry.vm) {
              println("@AppServer(version = \"$version\", jdk = \"$jdk${if (vm == "hotspot") "" else "-openj9"}\")")
              println("class ${serverName}${majorVersion}Jdk${jdk}${if (vm == "hotspot") "" else "Openj9"} extends ${serverName}SmokeTest {")
              println("}")
            }
          }
        }
      }
    }
  }
}

fun configureImage(
  parentTask: TaskProvider<out Task>,
  server: String,
  dockerfile: String,
  version: String,
  vm: String,
  jdk: String,
  warProject: String,
  args: Map<String, String>,
  isWindows: Boolean
): String {
  // Using separate build directory for different image
  val dockerWorkingDir = layout.buildDirectory.dir("docker-$server-$version-jdk$jdk-$vm-$warProject")
  val dockerFileName = "$dockerfile.${if (isWindows) "windows." else ""}dockerfile"
  val platformSuffix = if (isWindows) "-windows" else ""

  val prepareTask = tasks.register<Copy>("${server}ImagePrepare-$version-jdk$jdk-$vm$platformSuffix") {
    val warTask = project(":smoke-tests:images:servlet:$warProject").tasks.named<War>("war")
    dependsOn(warTask)
    into(dockerWorkingDir)
    from("src/$dockerFileName")
    from("src/main/docker/$server")
    from(warTask.get().archiveFile) {
      rename { "app.war" }
    }
  }

  val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"
  val vmSuffix = if (vm == "hotspot") "" else "-$vm"
  val image = "ghcr.io/$repo/smoke-test-servlet-$server:$version-jdk$jdk$vmSuffix$platformSuffix-$extraTag"

  val jdkImage = if (vm == "hotspot") {
    if (jdk == "24") {
      // "The only tags which will continue to receive updates beyond July 2022 will be Early Access
      // builds (which are sourced from jdk.java.net), as those are not published/supported by any
      // of the above projects."
      // (see https://hub.docker.com/_/openjdk)
      "openjdk:$jdk"
    } else {
      "eclipse-temurin:$jdk"
    }
  } else if (vm == "openj9") {
    if (isWindows) {
      // ibm-semeru-runtimes doesn't publish windows images
      throw GradleException("Unexpected vm: $vm")
    } else {
      "ibm-semeru-runtimes:open-$jdk-jdk"
    }
  } else {
    throw GradleException("Unexpected vm: $vm")
  }

  val extraArgs = args.toMutableMap()
  if (server == "wildfly") {
    // wildfly url without .zip or .tar.gz suffix
    val majorVersion = version.substring(0, version.indexOf(".")).toInt()
    val serverBaseUrl = if (majorVersion >= 25) {
      "https://github.com/wildfly/wildfly/releases/download/$version/wildfly-$version"
    } else {
      "https://download.jboss.org/wildfly/$version/wildfly-$version"
    }
    extraArgs["baseDownloadUrl"] = serverBaseUrl
  } else if (server == "payara") {
    if (version == "5.2020.6") {
      extraArgs["domainName"] = "production"
    } else {
      extraArgs["domainName"] = "domain1"
    }
  }

  val buildTask = tasks.register<DockerBuildImage>("${server}Image-$version-jdk$jdk$vmSuffix$platformSuffix") {
    dependsOn(prepareTask)
    group = "build"
    description = "Builds Docker image with $server $version on JDK $jdk-$vm${if (isWindows) " on Windows" else ""}"

    inputDir.set(dockerWorkingDir)
    images.add(image)
    dockerFile.set(File(dockerWorkingDir.get().asFile, dockerFileName))
    buildArgs.set(extraArgs + mapOf("jdk" to jdk, "vm" to vm, "version" to version, "jdkImage" to jdkImage))
    doLast {
      matrix.add(image)
    }
  }

  parentTask.configure {
    dependsOn(buildTask)
  }
  return image
}

fun createDockerTasks(parentTask: TaskProvider<out Task>, isWindows: Boolean) {
  val resultImages = mutableSetOf<String>()
  for ((server, matrices) in targets) {
    val smokeTestServer = findProperty("smokeTestServer")
    if (smokeTestServer != null && server != smokeTestServer) {
      continue
    }

    for (entry in matrices) {
      val dockerfile = server
      val extraArgs = entry.args
      val warProject = entry.war
      val supportsWindows = entry.windows

      for (version in entry.version) {
        for (vm in entry.vm) {
          if (vm == "openj9" && isWindows) {
            // ibm-semeru-runtimes doesn't publish windows images
            continue
          }
          for (jdk in entry.jdk) {
            if (supportsWindows || !isWindows) {
              resultImages.add(configureImage(parentTask, server, dockerfile, version, vm, jdk, warProject, extraArgs, isWindows))
            }
          }
        }
      }
    }
  }
}
