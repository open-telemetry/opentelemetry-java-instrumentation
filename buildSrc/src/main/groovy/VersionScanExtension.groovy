class VersionScanExtension {
  String group
  String module
  String versions
  boolean scanMethods = false
  boolean scanDependencies = false
  String legacyGroup
  String legacyModule
  Map<String, String> verifyPresent = Collections.emptyMap()
  List<String> verifyMissing = Collections.emptyList()
}
