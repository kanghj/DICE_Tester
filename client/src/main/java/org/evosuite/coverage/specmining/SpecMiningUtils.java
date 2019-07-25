package org.evosuite.coverage.specmining;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map.Entry;

import org.evosuite.Properties;
import org.evosuite.assertion.ArgumentValueTraceEntry;
import org.evosuite.assertion.NullTraceEntry;
import org.evosuite.assertion.OutputTrace;
import org.evosuite.assertion.OutputTraceEntry;
import org.evosuite.assertion.PrimitiveTraceEntry;
import org.evosuite.ga.metaheuristics.mosa.structural.MultiCriteriaManager;
import org.evosuite.seeding.ConstantPoolManager;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.statements.ConstructorStatement;
import org.evosuite.testcase.statements.MethodStatement;
import org.evosuite.testcase.statements.Statement;
import org.evosuite.testcase.variable.VariableReference;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpecMiningUtils {

	
	private static boolean isFirstRun = true;
	
	
	public final static boolean onlyName = true;
	public final static boolean writeParameterValues = false;
	
	private static boolean printedExample = false;
	private static Logger logger = LoggerFactory.getLogger(SpecMiningUtils.class);
	
	
	static final Set<String> vocab = new java.util.HashSet<>();
	static boolean checkedVocab = false;
	
	public static int hasNotLeak = 0;
	
	private static final Set<String> methodsThatCanThrow = new HashSet<>();
	
	
	// cache
	private static final Map<Class<?>, Set<String>> methodsOfClazz = new HashMap<>();
	private static final Map<Class<?>, BufferedWriter> classTracesWriter = new HashMap<>();
	
	public static boolean isVocabInitialized() {
		return !vocab.isEmpty() && checkedVocab;
	}

	
	public static void initVocab() {
		
		checkedVocab = true;
		String name = Properties.TARGET_CLASS;
		
		name = name.substring(name.lastIndexOf(".") + 1);
		String filePath = name.toLowerCase() + ".vocab.txt";
		try (BufferedReader br = new BufferedReader(
				new FileReader(
						filePath))
			) {
				String st; 
				while ((st = br.readLine()) != null)  {
					  vocab.add(st.trim());
				} 
				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		LoggingUtils.logWarnAtMostOnce(
			logger, 
			"vocab initialized! " + " size="+ vocab.size() + ". Init from " 
			+ filePath);
		
	}
	
	public static boolean isInVocab(String methodName) {
		if (!isVocabInitialized() ) {
			LoggingUtils.logWarnAtMostOnce(logger, "No vocab, so return true to everything "+ methodName );
			return true;
		}
		methodName = methodName.split("\\(")[0];
//		LoggingUtils.logWarnAtMostOnce(logger, "Checking if method is in vocab: " + methodName + ". Result= " + vocab.contains(methodName));

		return vocab.contains(methodName);
	}
	
	public static boolean isInit(ExecutionResult result, Statement stmt, boolean isExceptionThrownByStmt, TestCase test) {
		if (stmt instanceof MethodStatement) {
			MethodStatement ms = (MethodStatement) stmt;
			
			return ms.isStatic(); // TODO this is just a bad heuristic that if a method is static, it *might* be for initialization..
			
		}
		
		if (stmt instanceof ConstructorStatement) {
			
			return true;
		}
		
		return false;
	}

	
	private static boolean trackBooleanReturnValuesOnly = true;


	public static boolean checkLeak = false;
	
	@SuppressWarnings("unused")
	public static String methodName(ExecutionResult result, Statement stmt, boolean isExceptionThrownByStmt, 
			String exceptionThrown, 
			TestCase test, boolean keepParenthesis) {
		
			String methodName = null;
		
		if (stmt instanceof MethodStatement) {
			MethodStatement ms = (MethodStatement) stmt;
			methodName = ms.getMethodName() + ms.getDescriptor();
			
			List<VariableReference> parameters = ms.getParameterReferences();
					
			if (!keepParenthesis) {
				methodName = methodName.split("\\(")[0];
			}

			if (!trackBooleanReturnValuesOnly || 
					(ms.getReturnType().getTypeName().toLowerCase().equals("boolean") 
					|| ms.getReturnType().getTypeName().toLowerCase().contains("bool"))) {
				
				
				OutputTrace<?> primitiveTraces = result.getTrace(PrimitiveTraceEntry.class);
				OutputTrace<?> nullTraces = result.getTrace(NullTraceEntry.class);

				
				if (!ms.getReturnType().getTypeName().equals("void")) {
					// for now, adding arguments values only work if not returning void (because of some internal evosuite things)
					if (methodName.contains("range") &&  ms.getNumParameters() == 0) {
						logger.warn("range has 0 params");
						try {
							throw new RuntimeException("range has 0 params");
						} catch (Exception e) {
							logger.error("range has 0 params", e);
						}
					}
					if (writeParameterValues && keepParenthesis && !isExceptionThrownByStmt && ms.getNumParameters() > 0) {
						
						OutputTrace<?> argValTraces = result.getTrace(ArgumentValueTraceEntry.class);
						
						OutputTraceEntry argValEntry = argValTraces.getEntry(stmt.getPosition(), ms.getReturnValue());
//						
						if (argValEntry != null) {
							ArgumentValueTraceEntry argEntry = (ArgumentValueTraceEntry) argValEntry;
							List<VariableReference> arguments = argEntry.getVar();
							List<String> abstractedArguments = new ArrayList<>();
							assert arguments.size() == argEntry.getValues().size();
							
							for (int i = 0; i < arguments.size(); i++) {
								VariableReference arg = arguments.get(i);
								Object value = argEntry.getValues().get(i);
								abstractedArguments.add(
										abstractArgumentValue(value, arg.getType().getTypeName().toLowerCase()));
							}
							
							
							methodName += "{" + String.join("", abstractedArguments) + "}";
						} else {
							logger.warn("empty argument values at " + stmt.getPosition() + ", " + ms.getReturnValue());
							logger.warn("empty arguments");
							try {
								throw new RuntimeException("empty arguments");
							} catch (Exception e) {
								logger.error("empty arguments", e);
							}
							
							return null; // indicates that this trace should be disregarded
						}
					}
					
					if (primitiveTraces == null && nullTraces == null) {
						try {
							throw new RuntimeException("empty observer");
						} catch (Exception e) {
							logger.error("empty observer", e);
						}
					} else {
						;
						OutputTraceEntry primitiveEntry = primitiveTraces.getEntry(stmt.getPosition(), ms.getReturnValue());
						OutputTraceEntry nullEntry = nullTraces.getEntry(stmt.getPosition(), ms.getReturnValue());
						
						if (primitiveEntry != null) {
							PrimitiveTraceEntry valueEntry = (PrimitiveTraceEntry) primitiveEntry;
							
							try {
								String abstractedReturnValue = abstractReturnValue(
										valueEntry, ms.getReturnType().getTypeName().toLowerCase());
								methodName += ":" + abstractedReturnValue; //valueEntry.value.toString().toUpperCase();
							
							} catch (Exception e) {
								logger.error("error abstracting return value at " + stmt, e);
							}
	
							
						} else if (nullEntry != null) {
							
							NullTraceEntry nullValueEntry = (NullTraceEntry) nullEntry;
							
							try {
								String abstractedReturnValue = abstractReturnValue(
										nullValueEntry, ms.getReturnType().getTypeName().toLowerCase());
								methodName += ":" + abstractedReturnValue; //valueEntry.value.toString().toUpperCase();
							} catch (Exception e) {
								logger.error("error abstracting return value at " + stmt, e);
							}
							
						} else if (!isExceptionThrownByStmt) {
	
							if (!printedExample) {
		    					logger.warn("no return value at non-exceptional stmt");
		    					logger.warn("stmt " + stmt + "@" + stmt.getPosition());
		    					
		    					logger.warn(test.toCode());
		    					printedExample = true;
		    					try {
		    						throw new RuntimeException("no return value at non-exceptional stmt");
		    					} catch (Exception e) {
		    						logger.error("no return value at non-exceptional stmt", e);
		    						e.printStackTrace();
		    					}
	    					}
							
							logger.warn("return null due to missing return value!  : " +  ms.getReturnType().getTypeName());
							return null; // indicates that this trace should be disregarded
						}
					}
				}
			}
		}
		
		if (stmt instanceof ConstructorStatement) {
			ConstructorStatement ps = (ConstructorStatement) stmt;
			methodName = "<init>" + ps.getDescriptor();
			
			if (onlyName) {
				methodName = methodName.split("\\(")[0];
			}
			
		}
		
		if (isExceptionThrownByStmt) {
			methodName = "EXCEPTION:" + exceptionThrown.toUpperCase() + "(" + methodName + ")";  //"EXCEPTION(" + methodName + ")";
		}

		return methodName;
	}
	
	public static String methodName(ExecutionResult result, Statement stmt, boolean isExceptionThrownByStmt, 
			String exceptionThrown, 
			TestCase test) {
		return methodName(result, stmt, isExceptionThrownByStmt, exceptionThrown, test, !onlyName);
	}
	
	public static String abstractReturnValue(PrimitiveTraceEntry traceEntry, String lowerCasedType) {
		if (lowerCasedType.equals("bool") || lowerCasedType.equals("boolean")) {
			// either "true" or "false"
			return traceEntry.value.toString().toUpperCase();
		}
		
		if (lowerCasedType.startsWith("int")) {
			if ((int) traceEntry.value > 0) {
				return "1";
			} else if ((int)traceEntry.value == 0) {
				return "0";
			} else {
				return "-1";
			}
		}
		
		if (lowerCasedType.startsWith("long")) {
			if ((long) traceEntry.value > 0) {
				return "1";
			} else if ((long)traceEntry.value == 0) {
				return "0";
			} else {
				return "-1";
			}
		}
		
		return "";
		
		
	}
	
	
	public static String abstractArgumentValue(Object obj, String lowerCasedType) {
		if (lowerCasedType.equals("bool") || lowerCasedType.equals("boolean")) {
			// either "true" or "false"
			return obj.toString().toUpperCase();
		}
		
		if (lowerCasedType.startsWith("int")) {
			if ((int) obj> 0) {
				return "1";
			} else if ((int)obj == 0) {
				return "0";
			} else {
				return "-1";
			}
		}
		
		if (lowerCasedType.startsWith("long")) {
			if ((long) obj > 0) {
				return "1";
			} else if ((long)obj == 0) {
				return "0";
			} else {
				return "-1";
			}
		}
		
		if (lowerCasedType.startsWith("int")) {
			logger.warn("int argument type, but reaching object type logic : " + obj);
		}
			
		
		return obj == null? "null" : "non-null";
		
	}
	
	public static String abstractReturnValue(NullTraceEntry traceEntry, String lowerCasedType) {
		if (traceEntry.isNull) {
			return "null";
		} else {
			return "non-null";
		}
		
	}
	
	
	
	public static VariableReference tracedObject(Map<String, Set<Class<?>>> classOfVar, Statement stmt) {
		VariableReference objectToTrace = null;
		if (stmt instanceof MethodStatement) {
			MethodStatement ps = (MethodStatement) stmt;
			objectToTrace = ps.getCallee();
			Class<?> clazz = ps.getMethod().getDeclaringClass();
			

			
			if (objectToTrace == null) {
				return null;
			}
			String objectName = objectToTrace.getName();
			
			if (!classOfVar.containsKey(objectName)) {
				classOfVar.put(objectToTrace.getName(), new HashSet<>());
			}
			addToClassOfVar(classOfVar, objectName, clazz);
			
			
			Class<?>[] interfaces = clazz.getInterfaces();
			addInterfacesAndParentsToClassOfVar(classOfVar, objectName, interfaces, clazz.getSuperclass());
			

			
			
		}
		if (stmt instanceof ConstructorStatement) {
			ConstructorStatement ps = (ConstructorStatement) stmt;
			VariableReference thisObject = ps.getReturnValue();
			objectToTrace = thisObject;
			Class<?> clazz = ps.getConstructor().getDeclaringClass();
			if (objectToTrace == null) {
				return null;
			}
			
			String objectName = objectToTrace.getName();
			
			if (!classOfVar.containsKey(objectName)) {
				classOfVar.put(objectName, new HashSet<>());
			}
			
			addToClassOfVar(classOfVar, objectName, clazz);

			Class<?>[] interfaces = clazz.getInterfaces();
			addInterfacesAndParentsToClassOfVar(classOfVar, objectName, interfaces, clazz.getSuperclass());
			
//			LoggingUtils.logWarnAtMostOnce(logger, "superClass=" + clazz.getSuperclass() +" of=" + clazz);
		}
		return objectToTrace;
	}


	private static void addInterfacesAndParentsToClassOfVar(Map<String, Set<Class<?>>> classOfVar, String objectName,
			Class<?>[] interfaces, Class<?> superClass) {
		
		
		Set<Class<?>> interfacesToAdd = new HashSet<>();

		if (superClass != null) {
			classOfVar.get(objectName).add(superClass);
			Class<?>[] superClazzInterfaces = superClass.getInterfaces();
			interfacesToAdd.addAll(Arrays.asList(superClazzInterfaces));
			
			if (superClass.getSuperclass() != null) {
				interfacesToAdd.addAll(Arrays.asList(superClass.getSuperclass().getInterfaces()));
			}
		}
		
		
		interfacesToAdd.addAll(Arrays.asList(interfaces));
		
		for (Class<?> interfaceObject : interfacesToAdd) {
			addToClassOfVar(classOfVar, objectName, interfaceObject);
			
			for (Class<?> superInterface : interfaceObject.getInterfaces()) {
				addToClassOfVar(classOfVar, objectName, superInterface);	
//				LoggingUtils.logWarnAtMostOnce(logger, "super interface=" + superInterface +" of=" + interfaceObject.getSuperclass());
			}
			
			if (interfaceObject.getSuperclass() == null) {
				continue;
			}
			
			LoggingUtils.logWarnAtMostOnce(logger, "superClass=" + interfaceObject.getSuperclass() +" of=" + interfaceObject);

			addToClassOfVar(classOfVar, objectName, interfaceObject.getSuperclass());
			for ( Class<?> superInterfaceObject : interfaceObject.getSuperclass().getInterfaces()) {
				addToClassOfVar(classOfVar, objectName, superInterfaceObject);	
				LoggingUtils.logWarnAtMostOnce(logger, "interface=" + superInterfaceObject +" of=" + interfaceObject.getSuperclass());
				
				for (Class<?> superSuperInterfaceObject : superInterfaceObject.getInterfaces()) {
					addToClassOfVar(classOfVar, objectName, superSuperInterfaceObject);	
					LoggingUtils.logWarnAtMostOnce(logger, "super interface=" + superSuperInterfaceObject +" of=" + interfaceObject.getSuperclass());
				}
			}
			
			
			if (interfaceObject.getSuperclass().getSuperclass() == null) {
				continue;
			}
			
			LoggingUtils.logWarnAtMostOnce(logger, "superClass=" + interfaceObject.getSuperclass().getSuperclass() +" of=" + interfaceObject.getSuperclass());

			addToClassOfVar(classOfVar, objectName, interfaceObject.getSuperclass().getSuperclass());
			for ( Class<?> superInterfaceObject : interfaceObject.getSuperclass().getSuperclass().getInterfaces()) {
				addToClassOfVar(classOfVar, objectName, superInterfaceObject);	
				LoggingUtils.logWarnAtMostOnce(logger, "interface=" + superInterfaceObject +" of=" + interfaceObject.getSuperclass().getSuperclass());
				
				for (Class<?> superSuperInterfaceObject : superInterfaceObject.getInterfaces()) {
					addToClassOfVar(classOfVar, objectName, superSuperInterfaceObject);	
					LoggingUtils.logWarnAtMostOnce(logger, "super interface=" + superSuperInterfaceObject +" of=" + interfaceObject.getSuperclass());
				}
			}
		
		
		}
	}
	
	private static void addToClassOfVar(Map<String, Set<Class<?>>> classOfVar, String variableName, Class<?> clazz) {
		if (!clazz.getCanonicalName().contains("evosuite") && !clazz.getCanonicalName().contains("Cloneable")  && !clazz.getCanonicalName().contains("Serializable")) {
			classOfVar.get(variableName).add(clazz);
		}
		
	}
	
	public static Set<VariableReference> objectsToTrace(Map<String, Class<?>> classOfVar, Statement stmt) {
		
		return null;
	}
	
	
	public static void writeTracesToFile(List<String> methodEventNames, Class<?> clazz, String dirName) throws IOException {
		if (!classTracesWriter.containsKey(clazz)) {
			String clazzFileName = clazz.getName();
			classTracesWriter.put(clazz, new BufferedWriter(new FileWriter(dirName +  Properties.CONFIGURATION_ID +  "_" + clazzFileName + ".traces", true)));
		}
		
		BufferedWriter bufferedWriter = classTracesWriter.get(clazz);
		LoggingUtils.logWarnAtMostOnce(logger, "write to " +dirName + clazz.getName() + ".traces");
		bufferedWriter.write("<START> ");
		bufferedWriter.write(String.join(" ", methodEventNames));
		bufferedWriter.write(" <END>\n");
		bufferedWriter.flush();
	}
	
	public static void writeTracesToFile(List<TestCase> tests, List<ExecutionResult> results, 
										List<Boolean> hasLeaks, String dirName) throws IOException {
		assert tests.size() == results.size();
		assert tests.size() == hasLeaks.size();
	
		Set<String> knownCtor = new HashSet<>();
		
		for (int i = 0; i < results.size(); i++) {
			ExecutionResult result = results.get(i);
			TestCase test = tests.get(i);
			boolean hasLeak = hasLeaks.get(i);
			if (result == null || test == null) {
				logger.warn("result or test is null");
				continue;
			}
            Set<Integer> exceptionPositions = result.getPositionsWhereExceptionsWereThrown();
            
            Map<String, List<String>> tracesOfVar = new HashMap<>();	            
            
			Map<String, Set<Class<?>>> classOfVar = new HashMap<>();

			
			boolean hasUpdate = false;
			
    		for (Statement stmt : test) {
    			boolean isExceptionThrownByStmt = exceptionPositions.contains(stmt.getPosition());
				VariableReference objectToTrace = SpecMiningUtils.tracedObject(classOfVar, stmt);
				
//				if (isExceptionThrownByStmt) {
//					// let's break here because executing the code further down may throw an exception, but 
//					// evosuite never tried to execute them. And we shouldn't assume the code below it is correct.
//					
//					break;
//				}
				
				if (objectToTrace == null) {
					if (isExceptionThrownByStmt) {
//						logger.warn("stmt threw ex!");
						// let's break here because executing the code after this statement may throw an exception, but 
						// evosuite never tried to execute them. And we shouldn't assume the code after it doesn't throw.
						break;
	    			} else {
	    				continue;
	    			}
				}
		
				boolean onlyTrackingTargetClass = true;
				if (onlyTrackingTargetClass) {
					if (!stmt.getReturnClass().isAssignableFrom(Properties.getTargetClassAndDontInitialise())) {
	    			
						if (!classOfVar.get(objectToTrace.getName()).stream()
								.anyMatch(clazz -> clazz.isAssignableFrom(Properties.getTargetClassAndDontInitialise()))) {
							
//								logger.warn("different class " + Properties.TARGET_CLASS + " vs " + classOfVar.get(objectToTrace.getName()));
		    				continue;
						}
					} 
				}
    			

    			String methodName = SpecMiningUtils.methodName(result, stmt, isExceptionThrownByStmt, 
    					result.getExceptionThrownAtPosition(stmt.getPosition()) != null ? 
    							result.getExceptionThrownAtPosition(stmt.getPosition()).getClass().getSimpleName() : 
    								null, 
    					test);
//    			logger.warn("method name looks like >" + methodName + "<");
    			
    			boolean isInit = SpecMiningUtils.isInit(result, stmt, isExceptionThrownByStmt, test);
    			
    			if (methodName == null || methodName.endsWith("Z")) {
//    				logger.warn("method name is null. ");
    				break;
    			}
    			
    			if (!tracesOfVar.containsKey(objectToTrace.getName())) {
    				// first event for this object
    				if (!methodName.contains("init>") && !isInit) {
    					if (isExceptionThrownByStmt) {
    						methodsThatCanThrow.add(methodName);
		    				break;
		    			} 

    				}
    				
    				tracesOfVar.put(objectToTrace.getName(), new ArrayList<>());
    			}
    			
    			if (!isVocabInitialized()) {
    				initVocab();
    			}
    			if (isExceptionThrownByStmt) {
    				methodsThatCanThrow.add(methodName);
//					logger.warn("reached exception at stmt of " + stmt);
    				break;
    			}
    			if (!vocab.isEmpty() && !vocab.contains(methodName) && !isInit) {
    				continue;
//    				break; // need to break instead of continue because "CTOR <omitted> methodThatCannotGoFirst <omitted>" may be output otherwise 
    			}
    			
    			
			
				
				boolean isCtor = false;
				boolean needToChangeInitToClassName = false; // have to change for evaluation against DSM
    			if (needToChangeInitToClassName && methodName.contains("init")) {
    				String className = objectToTrace.getClassName();
    						
    				if (className.contains("List")) {
    					logger.warn("We have a list! methodName:" + methodName);
    				}
    				
    				// convert <init> constructor to the class name
//    				String[] nameWithoutFQ = Properties.TARGET_CLASS.split("\\.");
//    				String ctorName = nameWithoutFQ[nameWithoutFQ.length - 1];
//    				tracesOfVar.get(objectToTrace.getName()).append(nameWithoutFQ[nameWithoutFQ.length - 1] + " ");
    				methodName = className;
    				isCtor = true;
    			}
    			
//    			
    			tracesOfVar.get(objectToTrace.getName())
	    			.add(methodName);
    			
    			if (isCtor) {
    				knownCtor.add(methodName);
    			}
    			
    			if (methodName.contains("update")) {
    				logger.warn("has update" );
    				hasUpdate = true;
    			} else if (hasUpdate && methodName.contains("initVerify")) {
    				logger.warn("update then initVerify : " + test);
    			}
    			
    		}
    		
    		
    		if (hasLeak) {
    			for (Entry<String, List<String>> entry : tracesOfVar.entrySet()) {
    				entry.getValue().add( "<IO-LEAK>");
    			}
    		}
    		
    		Map<Class<?>, Set<String>> classToVar = new HashMap<>();
    		
    		for (Map.Entry<String, Set<Class<?>>> varClassesEntry : classOfVar.entrySet()) {
    			String variable = varClassesEntry.getKey();
    			Set<Class<?>> clazzes = varClassesEntry.getValue();
    			for (Class<?> clazz : clazzes) {
	    			if (!classToVar.containsKey(clazz)) {
	    				classToVar.put(clazz, new HashSet<>());
	    			}
	    			classToVar.get(clazz).add(variable);
    			}
    		}
    		
    		if (!dirName.endsWith("/")) {
    			dirName = dirName + "/";
    		}
    		for (Entry<Class<?>, Set<String>> classToVarEntry : classToVar.entrySet()) {
    			Class<?> clazz = classToVarEntry.getKey();
    			Set<String> variables = classToVarEntry.getValue();
    			
    			if (!methodsOfClazz.containsKey(clazz)) {
    				try {
		    			Set<String> clazzMethods = Arrays.asList(clazz.getMethods())
		    					.stream()
			    			.map(event -> event.getName())
			    			.collect(Collectors.toSet());
		    			clazzMethods.addAll(
		    					Arrays.asList(clazz.getConstructors()).stream()
		    					.map(event -> event.getName())
		    					.collect(Collectors.toSet()));
		    			methodsOfClazz.put(clazz, clazzMethods);
    				} catch (NoClassDefFoundError ncdfe) {
    					logger.error("Some kind of error with getting the methods!", ncdfe);
    					ncdfe.printStackTrace();
    				}
    			}
    			
    			
	    		
    			Set<List<String>> alreadyWritten = new HashSet<>();
    			
    			if (!classTracesWriter.containsKey(clazz)) {
    				String clazzFileName = clazz.getName();
    				
    				// use ,true (append) since its annoying later runs may override traces from older runs
    				// Must take care to delete all old traces for each round of experiment!
    				classTracesWriter.put(clazz, new BufferedWriter(new FileWriter(dirName +  Properties.CONFIGURATION_ID +  "_" + clazzFileName + ".traces", true)));
    				
    			}
    			
	    		try {
	    			BufferedWriter bufferedWriter = classTracesWriter.get(clazz);
	    			for (String variable : variables) {
	    				
	    				if (!tracesOfVar.containsKey(variable)) {
	    					continue;
	    				}
	    				List<String> traces = tracesOfVar.get(variable);
	    				List<String> filtered = traces.stream()
	    						.filter(event -> knownCtor.contains(event) 
	    								|| (methodsOfClazz.containsKey(clazz) && classMethodsInclude(methodsOfClazz.get(clazz), event)) 
	    								|| isInternal(event))
	    						.collect(Collectors.toList());
	    				if (alreadyWritten.contains(filtered)) {
	    					// skip writing a trace that we know for sure is duplicated.
	    					// Note that this doesn't completely prevent duplicates in the file
	    					// But prevent writing duplicates in the same batch of traces.
	    					continue; 
	    				}
	    				
		    			if (filtered.size() < 1) { // too short
		    				continue;
		    			}
		    			
		    			alreadyWritten.add(filtered);
		    			
		    			String trace = 	String.join(" ", filtered);
		    			bufferedWriter.write("<START> ");
			    		bufferedWriter.write(trace);
			    		bufferedWriter.write(" <END>\n");
			    		bufferedWriter.flush();
	    			}
		    	} catch (IOException ioe) {
		    		ioe.printStackTrace();
		    		logger.error("error writing " + clazz.getName(), ioe);
		    	}
    		}
		}
	}
	
	public static void writeCounterExampledProperties(Set<String> countered) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(Properties.CONFIGURATION_ID +  "_" + "countered.rules."+Properties.TARGET_CLASS))) {
			for (String counter : countered) {
				writer.write(counter);
				writer.write("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
			
			logger.warn("Unable to write counter-examples");
			logger.error("Unable to write counter-examples");
		}
	}
	
	
	public static void closeTraceFiles() throws IOException {
		for (BufferedWriter writer : classTracesWriter.values()) {
			writer.flush();
			writer.close();
		}
	}
	public static void writeSeperataor() throws IOException {
//		for (BufferedWriter writer : classTracesWriter.values()) {
//			writer.write("====\n");
//		}
		logger.warn("everything after this is for writing the traces of the FINAL test suite");
		logger.warn("DYNAMOSA number of times calculated fitness: " + MultiCriteriaManager.numberOfTimesCalucaltedFitness);
	}
	
	private static boolean isInternal(String event) {
		return event.startsWith("<");
	}
	
	private static boolean classMethodsInclude(Set<String> clazzMethods, String event) {
		String eventStripped = event.split("\\(")[0];
		boolean retVal = clazzMethods.contains(eventStripped);
		
		return retVal;
	}
	
	public static void writeThrowablesToFile() {
		String name = Properties.TARGET_CLASS.substring(Properties.TARGET_CLASS.lastIndexOf(".") + 1);
		String testDir = Properties.TEST_DIR;
		
		try {
			
			try (BufferedWriter bufferedWriter = new BufferedWriter(
					new FileWriter(testDir + File.separator + name + ".throws", true))) {
    			
	    		for (String method : methodsThatCanThrow) {
	
		    		bufferedWriter.write(method);
		    		
		    		bufferedWriter.write("\n");
	    		}
	    	}
		} catch (Exception e) {
			logger.error("Error writing traces of which method can throw to file: " + testDir + File.separator + name + ".throws", e);
		}
	}
	


	public static boolean isFirstRun() {
		return isFirstRun;
	}
	
	public static void notFirstRun() {
		isFirstRun = false;
	}
	
	public static void seedConstants() {
		// assuming on the macbook, set Remote Login to enabled. 
		if (Properties.TARGET_CLASS.contains("Sftp")) {
			ConstantPoolManager.getInstance().addDynamicConstant("localhost");
//			ConstantPoolManager.getInstance().addDynamicConstant("tester");
//			ConstantPoolManager.getInstance().addDynamicConstant("asdf");
			ConstantPoolManager.getInstance().addDynamicConstant("/Users/tester");
			ConstantPoolManager.getInstance().addDynamicConstant("test.txt");
			ConstantPoolManager.getInstance().addDynamicConstant("testdir");
		}
		// assuming  fakeSMTP running on localhost on port 25
		if (Properties.TARGET_CLASS.contains("SMTP")) {
			ConstantPoolManager.getInstance().addDynamicConstant("localhost");
			ConstantPoolManager.getInstance().addDynamicConstant(25);
		}
	}
	
	
	public static String pid() {
		String fullName = ManagementFactory.getRuntimeMXBean().getName();
		String pid = fullName.split("@")[0];
		return pid;
	}
	
	public static long shelloutToLsof(String pid) {
		ProcessBuilder processBuilder = new ProcessBuilder();

		processBuilder.command("lsof", "-a", "-p",  pid);
		long count = 0;

		try {

			Process process = processBuilder.start();

			StringBuilder output = new StringBuilder();

			BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));

			String line;
			
			while ((line = reader.readLine()) != null) {
				output.append(line + "\n");
				count += 1;
			}


		} catch (IOException e) {
			e.printStackTrace();
		} 
		return count;
	}
	
	
}
