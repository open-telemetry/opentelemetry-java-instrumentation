val stableVersion = "1.22.0"
val alphaVersion = "1.22.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
