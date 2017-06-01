package io.opentracing.contrib.agent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.datadoghq.trace.resolver.FactoryUtils;


/**
 * Utility class to check the validity of the classpath concerning the java automated instrumentations
 */
public class JarVersionsChecker {

	private static Logger log = Logger.getLogger(JarVersionsChecker.class.getName());
	
	public static String AUTHORIZED_VERSIONS_CONFIG ="dd-trace-authorized-versions.yaml";
	
	/**
	 * Retrieves all the jars from the classpath
	 */
	private static List<File> getJarsFromClasspath() {
		return JarVersionsChecker.getJarFiles(System.getProperty("java.class.path"));
	}

	/**
	 * list files in the given directory and subdirs (with recursion)
	 * @param paths
	 * @return
	 */
	public static List<File> getJarFiles(String paths) {
		List<File> filesList = new ArrayList<File>();
		for (final String path : paths.split(File.pathSeparator)) {
			final File file = new File(path);
			if( file.isDirectory()) {
				recurse(filesList, file);
			}
			else {
				if(file.getName().endsWith(".jar")){
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

	public static Pattern versionPattern = Pattern.compile("-(\\d+\\..+)\\.jar");
	public static String extractJarVersion(String jarName){
		Matcher matcher = versionPattern.matcher(jarName);
		if (matcher.find())
			return matcher.group(1);
		else
			return null;

	}


	public static void main(String args[]) throws Exception {
		checkJarVersions();
	}

	/**
	 * Check all Jar versions in the classpath
	 * 
	 * @return the list of jar keys that have been detected as potential issues
	 */
	@SuppressWarnings("unchecked")
	public static List<String> checkJarVersions() {
		List<String> potentialIssues = new ArrayList<>();
		
		//Load instrumentations versions
		Map<String, Map<String,String>> versions = FactoryUtils.loadConfigFromResource(AUTHORIZED_VERSIONS_CONFIG, Map.class);
		if(versions==null){
			log.log(Level.WARNING, "DD agent: Authorized versions configuration file {} not found in classpath. Cannot proceed to the Jar versions check.",AUTHORIZED_VERSIONS_CONFIG);
			return potentialIssues;
		}

		//Scan classpath provided jars
		List<File> jars = getJarsFromClasspath();
		for (File file: jars) {
			String jarName = file.getName();
			String versionRestrictions = extractJarVersion(jarName);

			if(versionRestrictions!=null){
				//Extract artifactId
				String artifactId = file.getName().substring(0, jarName.indexOf(versionRestrictions)-1);
				Map<String,String> restrictions = versions.get(artifactId);
				if(restrictions!=null){
					String versionPattern = restrictions.get("valid_versions");
					if(versionPattern!=null){
						if(!Pattern.matches(versionPattern, versionRestrictions)){
							String key = restrictions.get("key");
							if(key!=null){
								potentialIssues.add(key);
							}
							log.log(Level.WARNING, "DD agent: The JAR {} as been found in the classpath. It may create some intrumentation issue, some rules are about to get disabled.",jarName);
						}
					}
				}
			}
		}
		return potentialIssues;
	}


}