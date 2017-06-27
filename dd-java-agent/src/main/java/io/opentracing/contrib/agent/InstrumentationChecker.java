package io.opentracing.contrib.agent;

import com.datadoghq.trace.resolver.FactoryUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Utility class to check the validity of the classpath concerning the java automated instrumentations
 */
public class InstrumentationChecker {

	private static final String CONFIG_FILE = "dd-trace-supported-framework.yaml";
	private final Map<String, List<Map<String, String>>> rules;
	private final Map<String, String> frameworks;

	private static InstrumentationChecker INSTANCE;

	/* For testing purpose */
	InstrumentationChecker(Map<String, List<Map<String, String>>> rules, Map<String, String> frameworks) {
		this.rules = rules;
		this.frameworks = frameworks;
		INSTANCE = this;
	}

	private InstrumentationChecker() {
		rules = FactoryUtils.loadConfigFromResource(CONFIG_FILE, Map.class);
		frameworks = scanLoadedLibraries();

	}

	/**
	 * Return a list of unsupported rules regarding loading deps
	 *
	 * @return the list of unsupported rules
	 */
	public synchronized static List<String> getUnsupportedRules() {

		if (INSTANCE == null) {
			INSTANCE = new InstrumentationChecker();
		}

		return INSTANCE.doGetUnsupportedRules();
	}

	private List<String> doGetUnsupportedRules() {

		List<String> unsupportedRules = new ArrayList<>();
		for (String rule : rules.keySet()) {

			// Check rules
			boolean supported = false;
			for (Map<String, String> check : rules.get(rule)) {
				if (frameworks.containsKey(check.get("artifact"))) {
					boolean matched = Pattern.matches(check.get("supported_version"), frameworks.get(check.get("artifact")));
					if (!matched) {
						supported = false;
						break;
					}
					supported = true;
				}
			}

			if (!supported) {
				unsupportedRules.add(rule);
			}
		}

		return unsupportedRules;

	}


	private static Map<String, String> scanLoadedLibraries() {

		Map<String, String> frameworks = new HashMap<>();

		// Scan classpath provided jars
		List<File> jars = getJarFiles(System.getProperty("java.class.path"));
		for (File file : jars) {

			String jarName = file.getName();
			String version = extractJarVersion(jarName);

			if (version != null) {

				// Extract artifactId
				String artifactId = file.getName().substring(0, jarName.indexOf(version) - 1);

				// Store it
				frameworks.put(artifactId, version);
			}
		}

		return frameworks;
	}


	private static List<File> getJarFiles(String paths) {
		List<File> filesList = new ArrayList<File>();
		for (final String path : paths.split(File.pathSeparator)) {
			final File file = new File(path);
			if (file.isDirectory()) {
				recurse(filesList, file);
			} else {
				if (file.getName().endsWith(".jar")) {
					filesList.add(file);
				}
			}
		}
		return filesList;
	}

	private static void recurse(List<File> filesList, File f) {
		File list[] = f.listFiles();
		for (File file : list) {
			getJarFiles(file.getPath());
		}
	}

	private static String extractJarVersion(String jarName) {

		Pattern versionPattern = Pattern.compile("-(\\d+\\..+)\\.jar");
		Matcher matcher = versionPattern.matcher(jarName);
		if (matcher.find()) {
			return matcher.group(1);
		} else {
			return null;
		}
	}
}