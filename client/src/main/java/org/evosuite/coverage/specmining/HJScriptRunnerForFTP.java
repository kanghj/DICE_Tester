package org.evosuite.coverage.specmining;

public class HJScriptRunnerForFTP {

	private static String rootDir = "/home/kanghj/fuzzing/LightFTP/Source/Release";
	private static String command = "./fttp fttp.conf 2200";
	
	public static final boolean runningFTP = false;
	public static int executionCount = 0; //once this hits 100, then restart 

	public static void runServerIfCrossExecutionCountThreshold() {
		
		if (!runningFTP) return;
		
		executionCount += 1;
		
		if (executionCount > 100) {
		
			Process p;
			try {
				p =  Runtime.getRuntime().exec(command );
//		        p.waitFor();
			} catch (Exception e) {
		        e.printStackTrace();
		    }
			executionCount = 0;
		}
	}
	
}
