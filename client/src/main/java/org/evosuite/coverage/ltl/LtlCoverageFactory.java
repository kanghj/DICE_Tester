package org.evosuite.coverage.ltl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.tuple.Triple;
import org.evosuite.Properties;
import org.evosuite.setup.TestUsageChecker;
import org.evosuite.testsuite.AbstractFitnessFactory;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LtlCoverageFactory extends AbstractFitnessFactory<LtlCoverageTestFitness>  {
	private static Logger logger = LoggerFactory.getLogger(LtlCoverageFactory.class);

	// TODO probably should move to a different class
	public static String pathToMiningHome = "/workspace/dice_miner/";
	private static String pathToRuleFiles = pathToMiningHome + "ltl_";

	
	public static String pathToConfig = "config.txt";
	
	public  List<String> readSpecialConfig() {
		String changeClazz = "";
		String path = "";
		try (
			 BufferedReader br = new BufferedReader(
			 new FileReader(pathToConfig))) {
			String st; 
			  while ((st = br.readLine()) != null)  {
				  if (st.contains("class")) {
					  changeClazz = st.split("class:")[1].trim();
					  continue;
				  }
				  
				  if (st.contains("path")) {
					  path = st.split("path:")[1].trim();
					  continue;
				  }
			  }
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Arrays.asList(changeClazz, path);
	}
	
	 public List<LtlCoverageTestFitness> getCoverageGoals() {
		 
		 List<String> extraConfig = readSpecialConfig();
		 String extraConfigPath = extraConfig.get(1);
		 String extraConfigClazz = extraConfig.get(0);
		 boolean hasExtraConfig = !extraConfigPath.isEmpty(); //&& !extraConfigClazz.isEmpty();
		 
		 
	        List<LtlCoverageTestFitness> goals = new ArrayList<>();

	        String className = Properties.TARGET_CLASS;
			Class<?> clazz = Properties.getInitializedTargetClass();
			
			// TODO read the triples consisting of property_type, method 1, method 2
			List<Triple<String, String, String>> ltlTriples = new ArrayList<>();
			String name = clazz.getName().substring(clazz.getName().lastIndexOf(".") + 1);

			String path;

			if (!hasExtraConfig) {
				if (Properties.PATH_TO_LTL_RULES.isEmpty()) {
					logger.warn("Defaulting to BASE LTL paths AT " + pathToRuleFiles + name.toLowerCase() + ".txt");
					path = pathToRuleFiles + name.toLowerCase() + ".txt";
				} else {
					logger.warn("PATH (as configured) IS " + pathToRuleFiles + name.toLowerCase() + ".txt");
					path = Properties.PATH_TO_LTL_RULES; 
				}
		 } else {
				logger.warn("PATH (determined through config.txt) is " + extraConfigPath);
				path = extraConfigPath;
				
				if (!extraConfigClazz.isEmpty()) {
					// config class replaces the class
					try {
						clazz = Class.forName(extraConfigClazz);
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				} else {
					if (path.endsWith("/")) { // if is directory
						
						logger.warn("PATH (determined through config.txt) is a directory: " + path);
						name = clazz.getName().substring(clazz.getName().lastIndexOf(".") + 1);
						
						path += "ltl_" + name.toLowerCase() + ".txt";
						
						logger.warn("Therefore looking at PATH : " + path);
					}
				}
				

			 
		 }
			
			
			if (!(new File(path).exists())) {
				logger.warn("Unable to find LTL path. Reverting to " + name.toLowerCase() + ".txt");
				path = name.toLowerCase() + ".txt";
			}
			
			try (
				 BufferedReader br = new BufferedReader(
				 new FileReader(path))
			) {
				String st; 
				  while ((st = br.readLine()) != null)  {
				
					  String stripped = st.split("LTL:")[1];
					  String[] splitted = stripped.split("\\s");
					  Triple<String, String, String> triple = Triple.of(splitted[1], splitted[2], splitted[0]);
					  ltlTriples.add(triple);
				  } 
				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			
				
			for (Triple<String, String, String> ltlTriple : ltlTriples) {
//				LoggingUtils.logWarnAtMostOnce(logger, "LTL goal : " + ltlTriple);
//				logger.warn();
				goals.add(new LtlCoverageTestFitness(
						clazz, 
						convertCtorToSameForm(ltlTriple.getLeft()), 
						convertCtorToSameForm(ltlTriple.getMiddle()), 
						ltlTriple.getRight()));
				
			}
			
			logger.warn("found " + goals.size() + " ltl rules to falsify");

	        return goals;
	    }
	 
	 public String convertCtorToSameForm(String name) {
			boolean isCapitalFirstLetter = Character.isUpperCase(name.charAt(0));
			if (isCapitalFirstLetter) {
				String paramAndRetValPart = "";
				if (name.contains("(")) {
					paramAndRetValPart = "(" + name.split("\\(")[1];
				}
			
				return "<init>" + paramAndRetValPart;
			}
			
			return name;
			
	 }

}
