package io.opentracing.contrib.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.byteman.agent.Location;
import org.jboss.byteman.agent.LocationType;
import org.jboss.byteman.agent.Retransformer;
import org.jboss.byteman.agent.RuleScript;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import com.datadoghq.trace.resolver.AgentTracerConfig;
import com.datadoghq.trace.resolver.DDTracerFactory;
import com.datadoghq.trace.resolver.FactoryUtils;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.Descriptor;

/**
 * This manager is loaded at pre-main.
 * 
 * It loads all the scripts contained in all the 'oatrules.btm' resource files then instrument all the methods annoted with the @Trace.
 */
public class TraceAnnotationsManager {
	private static Logger log = Logger.getLogger(TraceAnnotationsManager.class.getName());

	private static Retransformer transformer;

	private static final String AGENT_RULES = "otarules.btm";

	/**
	 * This method initializes the manager.
	 *
	 * @param trans The ByteMan retransformer
	 * @throws Exception
	 */
	public static void initialize(Retransformer trans) throws Exception {
		transformer = trans;
		//Load configuration
		AgentTracerConfig agentTracerConfig = FactoryUtils.loadConfigFromFilePropertyOrResource(DDTracerFactory.SYSTEM_PROPERTY_CONFIG_PATH,DDTracerFactory.CONFIG_PATH, AgentTracerConfig.class);
		
		List<String> loadedScripts = loadRules(ClassLoader.getSystemClassLoader());
		
		//Check if some rules have to be uninstalled
		List<String> uninstallScripts = JarVersionsChecker.checkJarVersions();
		if(agentTracerConfig != null){
			List<String> disabledInstrumentations = agentTracerConfig.getDisabledInstrumentations();
			if(disabledInstrumentations!=null && !disabledInstrumentations.isEmpty()){
				uninstallScripts.addAll(disabledInstrumentations);
			}
		}
		uninstallScripts(loadedScripts,uninstallScripts);

		//Check if annotations are enabled
		if(agentTracerConfig != null 
				&& agentTracerConfig.getEnableCustomAnnotationTracingOver()!=null
				&& agentTracerConfig.getEnableCustomAnnotationTracingOver().length>0){
			loadAnnotationsRules(agentTracerConfig.getEnableCustomAnnotationTracingOver());
		}
	}

	/**
	 * Uninstall some scripts from a list of patterns.
	 * All the rules that contain the pattern will be uninstalled 
	 * 
	 * @param installedScripts 
	 * @param patterns not case sensitive (eg. "mongo", "apache http", "elasticsearch", etc...])
	 */
	public static void uninstallScripts(List<String> installedScripts, List<String> patterns) throws Exception{
		Set<String> rulesToRemove = new HashSet<String>();

		for(String strPattern : patterns){
			Pattern pattern = Pattern.compile("(?i)RULE [^\n]*"+strPattern+"[^\n]*\n");
			for(String loadedScript : installedScripts){
				Matcher matcher = pattern.matcher(loadedScript);
				while(matcher.find()){
					rulesToRemove.add(matcher.group());
				}
			}
		}
		
		if(!rulesToRemove.isEmpty()){
			StringWriter sw = new StringWriter();
			try(PrintWriter pr = new PrintWriter(sw)){
				transformer.removeScripts(new ArrayList<String>(rulesToRemove), pr);
			}
			log.log(Level.INFO, sw.toString());
		}
	}

	/**
	 * This method loads any OpenTracing Agent rules (otarules.btm) found as resources
	 * within the supplied classloader.
	 *
	 * @param classLoader The classloader
	 */
	public static List<String> loadRules(ClassLoader classLoader) {
		List<String> scripts = new ArrayList<>();
		if (transformer == null) {
			log.severe("Attempt to load OpenTracing agent rules before transformer initialized");
			return scripts;
		}
	
		List<String> scriptNames = new ArrayList<>();
	
		// Load default and custom rules
		try {
			Enumeration<URL> iter = classLoader.getResources(AGENT_RULES);
			while (iter.hasMoreElements()) {
				loadRules(iter.nextElement().toURI(), scriptNames, scripts);
			}
	
			StringWriter sw=new StringWriter();
			try (PrintWriter writer = new PrintWriter(sw)) {
				try {
					transformer.installScript(scripts, scriptNames, writer);
				} catch (Exception e) {
					log.log(Level.SEVERE, "Failed to install scripts", e);
				}
			}
			if (log.isLoggable(Level.FINEST)) {
				log.finest(sw.toString());
			}
		} catch (IOException | URISyntaxException e) {
			log.log(Level.SEVERE, "Failed to load OpenTracing agent rules", e);
		}
	
		if (log.isLoggable(Level.FINE)) {
			log.fine("OpenTracing Agent rules loaded");
		}
		return scripts;
	}

	/**
	 * Find all the methods annoted with @Trace and inject rules
	 * @param scannedPackages 
	 */
	public static void loadAnnotationsRules(String... scannedPackages) {
		
		log.info("Looking for annotations over the following packages: "+Arrays.asList(scannedPackages));
		long cur = System.currentTimeMillis();
		
		Reflections reflections = new Reflections(new ConfigurationBuilder()
				.forPackages(scannedPackages)
				.filterInputsBy(new FilterBuilder().includePackage(scannedPackages).include(".*\\.class"))
				.setScanners(new MethodAnnotationsScanner()));

		Set<Method> methods = reflections.getMethodsAnnotatedWith(Trace.class);

		StringBuilder generatedScripts = new StringBuilder();
		for(Method method : methods){
			try{
				ClassPool pool = ClassPool.getDefault();
				CtClass cc = pool.get(method.getDeclaringClass().getCanonicalName());
				CtMethod javassistMethod = cc.getDeclaredMethod(method.getName());

				//AT ENTRY: child of current case
				String ruleText = 
						CURRENT_SPAN_EXISTS+
						buildSpan(javassistMethod)+
						buildWithTags(javassistMethod)+
						START;
				RuleScript script = createRuleScript("Start Active Span ",cc, javassistMethod,  Location.create(LocationType.ENTRY,""),ruleText);
				generatedScripts.append(script).append("\n");

				//AT EXIT
				script = createRuleScript("Close span ",cc, javassistMethod, Location.create(LocationType.EXIT,""),EXIT_RULE);
				generatedScripts.append(script).append("\n");

			}catch(Exception e){
				log.log(Level.SEVERE,"Could not create rule for method "+method+". Proceed to next annoted method.",e);
			}
		}
		try {
			StringWriter sw = new StringWriter();
			try(PrintWriter pr = new PrintWriter(sw)){
				transformer.installScript(Arrays.asList(generatedScripts.toString()),Arrays.asList("@Trace annotations"),pr);
			}
			log.log(Level.FINE, sw.toString());
		} catch (Exception e) {
			log.log(Level.SEVERE,"Could not install annotation scripts.",e);
		}
		
		log.info("Finished annotation scanning in " + (System.currentTimeMillis() - cur) +" ms. You can accelerate this process by refining the packages you want to scan with `scannedPackages` in the dd-trace.yaml configuration file.");
	}

	private static RuleScript createRuleScript(
			String ruleNamePrefix,
			CtClass cc,
			CtMethod javassistMethod,
			Location loc,
			String ruleText) {
		int lineNumber = javassistMethod.getMethodInfo().getLineNumber(0);
		String[] imports = new String[0];


		RuleScript ruleScript = new RuleScript(
				ruleNamePrefix+loc+" "+javassistMethod.getLongName(),
				cc.getName(),
				false, 
				false,
				javassistMethod.getName() + Descriptor.toString(javassistMethod.getSignature()),
				"io.opentracing.contrib.agent.OpenTracingHelper",
				imports,
				loc,
				ruleText,
				lineNumber,
				"",
				false);
		return ruleScript;
	}

	private static void loadRules(URI uri, final List<String> scriptNames,
			final List<String> scripts) throws IOException {
		if (log.isLoggable(Level.FINE)) {
			log.fine("Load rules from URI = " + uri);
		}

		StringBuilder str=new StringBuilder();
		try (InputStream is = uri.toURL().openStream()) {
			byte[] b = new byte[10240];
			int len;
			while ((len = is.read(b)) != -1) {
				str.append(new String(b, 0, len));
			}
		}
		scripts.add(str.toString());
		scriptNames.add(uri.toString());
	}

	private static String CURRENT_SPAN_EXISTS = "IF TRUE\n";

	private static String BUILD_SPAN = "DO\n"+"getTracer().buildSpan(\"";
	private static String CLOSE_PARENTHESIS = "\")";

	private static String START = ".startActive();";

	private static String EXIT_RULE = "IF getTracer().activeSpan() != null\n"+
			"DO\n"+ 
			"getTracer().activeSpan().deactivate();\n";

	private static String buildSpan(CtMethod javassistMethod){
		try {
			Trace trace = (Trace) javassistMethod.getAnnotation(Trace.class);
			if(trace.operationName()!=null & !trace.operationName().isEmpty()){
				return BUILD_SPAN+trace.operationName()+CLOSE_PARENTHESIS;
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Error when building injection rule on method " + javassistMethod + ". Fallback on default value.", e);
		}
		return BUILD_SPAN+javassistMethod.getName()+CLOSE_PARENTHESIS;
	};

	private static String buildWithTags(CtMethod javassistMethod){
		try {
			Trace trace = (Trace) javassistMethod.getAnnotation(Trace.class);
			if(trace.tagsKV()!=null && trace.tagsKV().length>0){
				if(trace.tagsKV().length%2==0){
					StringBuilder sb = new StringBuilder();
					for(int i = 0;i<trace.tagsKV().length;i=i+2){
						sb.append(".withTag(\"")
						.append(trace.tagsKV()[i]).append("\",\"").append(trace.tagsKV()[i+1])
						.append(CLOSE_PARENTHESIS);
					}
					return sb.toString();
				}else{
					throw new IllegalArgumentException("The 'tagsKV' annotation attribute must define Key/Value pairs only");
				}
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Error when building injection rule on method " + javassistMethod + ". Fallback on default value.", e);
		}
		return "";
	};
}
