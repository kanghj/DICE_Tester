package org.evosuite.coverage.specmining;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.evosuite.classpath.ResourceList;
import org.evosuite.runtime.instrumentation.RuntimeInstrumentation;
import org.evosuite.runtime.util.ComputeClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class FindConstants extends ClassVisitor {
	
	public static Set<String> niceStrings = new HashSet<>();
	
	  public FindConstants(ClassVisitor visitor) {
		  super(Opcodes.ASM5, visitor);
		// TODO Auto-generated constructor stub
	}

	public MethodVisitor visitMethod(int access, String name, String desc, 
	       String signature, String[] exceptions) {
	    return new MethodVisitor(Opcodes.V1_7) {
	      public void visitLdcInsn(Object cst) {
	        if (cst instanceof String) {
	        	niceStrings.add((String) cst); 
	        }
	      }
	    };
	  }

	
	public static void getStrings(ClassLoader classLoader, String className, ClassReader reader) {
		String classNameWithDots = ResourceList.getClassNameFromResourcePath(className);

		if (!RuntimeInstrumentation.checkIfCanInstrument(classNameWithDots)) {
			throw new RuntimeException("Should not transform a shared class (" + classNameWithDots
					+ ")! Load by parent (JVM) classloader.");
		}
		
		int asmFlags = ClassWriter.COMPUTE_FRAMES;
		ClassWriter writer = new ComputeClassWriter(asmFlags);

		ClassVisitor cv = writer;
		
		cv = new FindConstants(cv);
	}
}
