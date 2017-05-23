package io.opentracing.contrib.agent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.byteman.agent.Location;
import org.jboss.byteman.agent.LocationType;
import org.jboss.byteman.agent.Retransformer;
import org.jboss.byteman.agent.RuleScript;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.Descriptor;

/**
 * This manager is loaded at pre-main.
 * 
 * It loads all the scripts contained in all the 'oatrules.btm' resource files then instrument all the methods annoted with the @Trace.
 */
public class TraceAnnotationsManager extends OpenTracingManager{
	private static Logger log = Logger.getLogger(TraceAnnotationsManager.class.getName());

	 private static Retransformer transformer;
	
	/**
	 * This method initializes the manager.
	 *
	 * @param trans The ByteMan retransformer
	 * @throws Exception
	 */
	public static void initialize(Retransformer trans) throws Exception {
		transformer = trans;
		OpenTracingManager.initialize(trans);
		loadRules(ClassLoader.getSystemClassLoader());
	}

	/**
	 * Find all the methods annoted with @Trace and inject rules
	 * 
	 * @param classLoader
	 */
	public static void loadRules(ClassLoader classLoader) {
		OpenTracingManager.loadRules(classLoader);

		Reflections reflections = new Reflections(new ConfigurationBuilder()
				.forPackages("/")
				.filterInputsBy(new FilterBuilder().include(".*\\.class"))
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
						CHILD_OF_CURRENT_SPAN+
						START;
				RuleScript script = createRuleScript("Child of ",cc, javassistMethod,  Location.create(LocationType.ENTRY,""),ruleText);
				generatedScripts.append(script).append("\n");
				
				//AT ENTRY: new trace
				ruleText = 
						CURRENT_SPAN_NOT_EXISTS+
						buildSpan(javassistMethod)+
						buildWithTags(javassistMethod)+
						START;
				script = createRuleScript("New trace ",cc, javassistMethod,  Location.create(LocationType.ENTRY,""),ruleText);
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
				"^"+cc.getName(),
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
	
	private static String CURRENT_SPAN_EXISTS = "IF currentSpan() != null\n";
	private static String CURRENT_SPAN_NOT_EXISTS = "IF currentSpan() == null\n";
	
	private static String BUILD_SPAN = "DO\n"+"activateSpan(getTracer().buildSpan(\"";
	private static String CLOSE_PARENTHESIS = "\")";
	
	private static String CHILD_OF_CURRENT_SPAN = ".asChildOf(currentSpan())";
	private static String START = ".start());";

	private static String EXIT_RULE = "IF currentSpan() != null\n"+
			"DO\n"+ 
			"currentSpan().finish();\n"+
			"deactivateCurrentSpan();";
	
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
