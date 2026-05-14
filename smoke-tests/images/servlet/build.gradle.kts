import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

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

abstract class DockerBuildService : BuildService<BuildServiceParameters.None>

gradle.sharedServices.registerIfAbsent("dockerBuildService", DockerBuildService::class.java) {
  maxParallelUsages.set(1)
}

val extraTag = findProperty("extraTag")
  ?: java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(java.time.LocalDateTime.now())

val latestJava = "25" // renovate(java-version)

// Each line under appserver describes one matrix of (version x vm x jdk), dockerfile key overrides
// Dockerfile name, args key passes raw arguments to docker build
val targets = mapOf(
  "jetty" to listOf(
    ImageTarget(
      listOf("9.4.58"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21", latestJava),
      mapOf("sourceVersion" to "9.4.58.v20250814")
    ),
    ImageTarget(
      listOf("10.0.26"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21", latestJava),
      mapOf("sourceVersion" to "10.0.26")
    ),
    ImageTarget(
      listOf("11.0.26"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21", latestJava),
      mapOf("sourceVersion" to "11.0.26"),
      "servlet-5.0"
    ),
    ImageTarget(
      listOf("12.0.28"),
      listOf("hotspot", "openj9"),
      listOf("17", "21", latestJava),
      mapOf("sourceVersion" to "12.0.28"),
      "servlet-5.0"
    ),
  ),
  "liberty" to listOf(
    ImageTarget(
      listOf("open-liberty:20.0.0.12-full-java11-openj9@sha256:2fa4af95d6c48e3db79edfd2b8a9c71e26c63a68c3fcae92f222fbb42c469ed2"),
      listOf("hotspot", "openj9"),
      listOf("8", "11")
    ),
    ImageTarget(
      listOf("open-liberty:21.0.0.12-full-java11-openj9@sha256:eb014c600b5e08b799cb0c5781e606cf1e7a28ad913ba956c9d9e7f8a2f528dc"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17")
    ),
    ImageTarget(
      listOf("open-liberty:22.0.0.12-full-java11-openj9@sha256:a06f1da35a564f00354b86c7d01d8cc9d6eef156ce88d5b59605c5c02bf48c72"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17")
    ),
    ImageTarget(
      listOf("open-liberty:23.0.0.12-full-java11-openj9@sha256:cd6aa69cffffb45427cbb6a5640cd00b13c98064f296a66894ea1decd181e1c3"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21")
    ),
    ImageTarget(
      listOf("open-liberty:26.0.0.3-full-java11-openj9@sha256:0f1e49d15b6de21cdf65b032a20103598c30f20f764b45ba1044d36d762f6165"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21", "25"),
      mapOf("release" to "26.0.0.3")
    ),
  ),
  "payara" to listOf(
    ImageTarget(
      listOf(
        "payara/server-full:5.2020.6@sha256:8c8f054ecbfb340b60961d7ffea2d223cea1afe6183f6986f4806de5c0bc9419",
        "payara/server-full:5.2021.8@sha256:ffc915a7243b27504c13c4bd4adb3da55c6c08a93ac05685afea3ea77380109d"
      ),
      listOf("hotspot", "openj9"),
      listOf("8", "11")
    ),
    // Test application is not deployed when server is sarted with hotspot jdk version 21
    ImageTarget(
      listOf("payara/server-full:6.2023.12@sha256:5c382db1f5bad8bef693dbcd0fed299844690839f4894e07c248de5f4b186b9b"),
      listOf("hotspot"),
      listOf("11", "17"),
      war = "servlet-5.0"
    ),
    ImageTarget(
      listOf("payara/server-full:6.2023.12@sha256:5c382db1f5bad8bef693dbcd0fed299844690839f4894e07c248de5f4b186b9b"),
      listOf("openj9"),
      listOf("11", "17", "21"),
      war = "servlet-5.0"
    )
  ),
  "tomcat" to listOf(
    ImageTarget(
      listOf("7.0.109"),
      listOf("hotspot", "openj9"),
      listOf("8"),
      mapOf("majorVersion" to "7")
    ),
    ImageTarget(
      listOf("8.5.98"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21", latestJava),
      mapOf("majorVersion" to "8")
    ),
    ImageTarget(
      listOf("9.0.111"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21", latestJava),
      mapOf("majorVersion" to "9")
    ),
    ImageTarget(
      listOf("10.1.48"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21", latestJava),
      mapOf("majorVersion" to "10"),
      "servlet-5.0"
    ),
  ),
  "tomee" to listOf(
    ImageTarget(
      listOf("7.0.9", "7.1.4"),
      listOf("hotspot", "openj9"),
      listOf("8")
    ),
    ImageTarget(
      listOf("8.0.16"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21", latestJava)
    ),
    ImageTarget(
      listOf("9.1.3"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21", latestJava),
      war = "servlet-5.0"
    ),
  ),
  "websphere" to listOf(
    ImageTarget(
      listOf(
        "icr.io/appcafe/websphere-traditional:8.5.5.29@sha256:5d11ebb08f1f99e43fc1386149b6df6378a2d61c0511ce48318b5c4ae8228f89",
        "icr.io/appcafe/websphere-traditional:9.0.5.27@sha256:f9c5c9f4fb45ddf989c56dec48d75dd85579fa60d86be831c4cbfada94bca0d4"
      ),
      listOf("openj9"),
      listOf("8"),
      windows = false
    ),
  ),
  "wildfly" to listOf(
    ImageTarget(
      listOf("13.0.0.Final"),
      listOf("hotspot", "openj9"),
      listOf("8")
    ),
    ImageTarget(
      listOf("17.0.1.Final", "21.0.0.Final"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21")
    ),
    ImageTarget(
      listOf("28.0.1.Final", "29.0.1.Final", "30.0.1.Final"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21"),
      war = "servlet-5.0"
    ),
  ),
)

tasks {
  val buildLinuxTestImages by registering {
    group = "build"
    description = "Builds all Linux Docker images for the test matrix"
  }

  val buildWindowsTestImages by registering {
    group = "build"
    description = "Builds all Windows Docker images for the test matrix"
  }

  val linuxImages = createDockerTasks(buildLinuxTestImages, false)
  val windowsImages = createDockerTasks(buildWindowsTestImages, true)

  val pushLinuxImages by registering(DockerPushImage::class) {
    dependsOn(buildLinuxTestImages)
    group = "publishing"
    description = "Push Linux Docker images for the test matrix"
    images.set(linuxImages)
  }

  val pushWindowsImages by registering(DockerPushImage::class) {
    dependsOn(buildWindowsTestImages)
    group = "publishing"
    description = "Push Windows Docker images for the test matrix"
    images.set(windowsImages)
  }

  val printSmokeTestsConfigurations by registering {
    doFirst {
      for ((server, matrices) in targets) {
        val smokeTestServer = findProperty("smokeTestServer")
        if (smokeTestServer != null && server != smokeTestServer) {
          continue
        }
        println(server)
        val serverName = server.replaceFirstChar(Char::uppercase)
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
}

fun configureImage(
  parentTask: TaskProvider<out Task>,
  server: String,
  dockerfile: String,
  fullVersion: String,
  vm: String,
  jdk: String,
  warProject: String,
  args: Map<String, String>,
  isWindows: Boolean
): String {
  val versionParts = fullVersion.split("@")
  val imageNameWithTag = versionParts[0]
  val serverImageHash = if (versionParts.size > 1) versionParts[1].removePrefix("sha256:") else ""

  // Extract just the version (tag) from the full image reference
  var version = if (imageNameWithTag.contains(":")) {
    imageNameWithTag.substringAfterLast(":")
  } else {
    imageNameWithTag
  }

  // Extract just the version number from tags with suffixes
  // (e.g., "20.0.0.12" from "20.0.0.12-full-java11-openj9")
  if (version.contains("-")) {
    version = version.substringBefore("-")
  }

  val dockerFileName = "$dockerfile.${if (isWindows) "windows." else ""}dockerfile"
  val platformSuffix = if (isWindows) "-windows" else ""

  // Using separate build directory for different images
  val dockerWorkingDir = layout.buildDirectory.dir("docker-$server-$version-jdk$jdk-$vm-$warProject-$platformSuffix")

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
    if (jdk == "26-ea") {
      // "The only tags which will continue to receive updates beyond July 2022 will be Early Access
      // builds (which are sourced from jdk.java.net), as those are not published/supported by any
      // of the above projects."
      // (see https://hub.docker.com/_/openjdk)
      "openjdk:$jdk"
    } else if (isWindows) {
      when (jdk) {
        "8" -> "eclipse-temurin:8u472-b08-jdk-windowsservercore-ltsc2022@sha256:2f2dc58147a9877ecde8644961b1e3c0f26f838af038ec8b8fc04dfbea61a4d0"
        "11" -> "eclipse-temurin:11.0.31_11-jdk-windowsservercore-ltsc2022@sha256:0f54f3b6cadaa4f7020491cf9fdfead74e4beae7a54c74859852567ff3c6a817"
        "17" -> "eclipse-temurin:17.0.19_10-jdk-windowsservercore-ltsc2022@sha256:9f54bfffb58a43e737e84922780660107a329d513e623d2d6bf5adac3fb92dd7"
        "21" -> "eclipse-temurin:21.0.11_10-jdk-windowsservercore-ltsc2022@sha256:623688f10feff4036496c832c37a789ee49bb5486a628d0938a377d97be40012"
        "25" -> "eclipse-temurin:25.0.3_9-jdk-windowsservercore-ltsc2022@sha256:d8cb8bf5bf5bdc326acd49c20da3cc211b74f80540c097de108321bf698a4bd2"
        else -> throw GradleException("Unexpected jdk version for Windows: $jdk")
      }
    } else {
      when (jdk) {
        "8" -> "eclipse-temurin:8u472-b08-jdk@sha256:0b793df1b9217f3d25c5f820d47e85a20b0a78b0ccd0ab6deb9051502493c855"
        "11" -> "eclipse-temurin:11.0.31_11-jdk@sha256:3221f763ed01df9fbaef81f74bc71f657b9e06e284a569c392afdc15c891afb0"
        "17" -> "eclipse-temurin:17.0.19_10-jdk@sha256:9cb1a90ce9b7921fe79e544200b0ea1eab76943dd84fb284356ddb8c96e8f32a"
        "21" -> "eclipse-temurin:21.0.11_10-jdk@sha256:c6a985d3be56d867557f8afc6c6786fd5a39ddf2e061635f2096d6b918f56b70"
        "25" -> "eclipse-temurin:25.0.3_9-jdk@sha256:e23592541431eaeef5c13c84c21db71f97cdca0e70181ea6222ec9bccac24f6c"
        else -> throw GradleException("Unexpected jdk version for Linux: $jdk")
      }
    }
  } else if (vm == "openj9") {
    if (isWindows) {
      // ibm-semeru-runtimes doesn't publish windows images
      throw GradleException("Unexpected vm: $vm")
    } else {
      when (jdk) {
        "8" -> "ibm-semeru-runtimes:open-8u472-b08-jdk@sha256:779c0c1133ebac0d599012c5a908e67adaa993352072eac21d7ced8d6a47f14d"
        "11" -> "ibm-semeru-runtimes:open-11.0.29_7-jdk@sha256:00bbefbb2cf3690546338c0e4ba4cf85ec658f40de5b292e77774b55e8267d66"
        "17" -> "ibm-semeru-runtimes:open-17-jdk@sha256:1020454301090cb4d7486671a282edd36ba31710f726d53cf0147cdc560163ab"
        "21" -> "ibm-semeru-runtimes:open-21.0.9_10-jdk@sha256:2edabc89c49cfa2b9f0c051aced57ca6dee81c2e6b8820a1257182e779b58a48"
        "25" -> "ibm-semeru-runtimes:open-25-jdk@sha256:40387ac3961775eb0bb635fee288f6f2307fdbb6e9f11d1f3d1fa6e2c052f5be"
        else -> throw GradleException("Unexpected jdk version for openj9: $jdk")
      }
    }
  } else {
    throw GradleException("Unexpected vm: $vm")
  }

  val jdkImageParts = jdkImage.split("@")
  val jdkImageName = jdkImageParts[0]
  val jdkImageHash = if (jdkImageParts.size > 1) jdkImageParts[1].removePrefix("sha256:") else ""

  val extraArgs = args.toMutableMap()

  // Pass the full image name with tag (for servers that need it)
  if (imageNameWithTag.contains(":")) {
    extraArgs["imageName"] = imageNameWithTag
  }

  if (server == "wildfly") {
    // wildfly url without .zip or .tar.gz suffix
    extraArgs["baseDownloadUrl"] = "https://repo1.maven.org/maven2/org/wildfly/wildfly-dist/$version/wildfly-dist-$version"
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

    usesService(gradle.sharedServices.registrations["dockerBuildService"].service)

    inputDir.set(dockerWorkingDir)
    images.add(image)
    dockerFile.set(File(dockerWorkingDir.get().asFile, dockerFileName))
    buildArgs.set(extraArgs + mapOf("jdk" to jdk, "vm" to vm, "version" to version, "jdkImageName" to jdkImageName, "jdkImageHash" to jdkImageHash, "imageHash" to serverImageHash))
  }

  parentTask.configure {
    dependsOn(buildTask)
  }
  return image
}

fun createDockerTasks(parentTask: TaskProvider<out Task>, isWindows: Boolean): Set<String> {
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
  return resultImages
}
