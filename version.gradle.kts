val stableVersion = "2.29.0"
val alphaVersion = "2.29.0-alpha"

val apidiffBaselineVersion = "2.28.1"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
