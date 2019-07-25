/**
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.assertion;

import org.evosuite.testcase.statements.Statement;
import org.evosuite.testcase.variable.VariableReference;

import java.util.ArrayList;
import java.util.List;

import org.evosuite.testcase.execution.CodeUnderTestException;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.execution.Scope;
import org.evosuite.testcase.statements.ArrayStatement;
import org.evosuite.testcase.statements.ConstructorStatement;
import org.evosuite.testcase.statements.EntityWithParametersStatement;
import org.evosuite.testcase.statements.FunctionalMockStatement;
import org.evosuite.testcase.statements.MethodStatement;
import org.evosuite.testcase.statements.PrimitiveStatement;

public class ArgumentValueTraceObserver extends AssertionTraceObserver<ArgumentValueTraceEntry> {

	/** {@inheritDoc} */
	@Override
	public synchronized void afterStatement(Statement statement, Scope scope,
	        Throwable exception) {
		// By default, no assertions are created for statements that threw exceptions
		if(exception != null)
			return;

		// No assertions are created for mock statements
		if(statement instanceof FunctionalMockStatement)
			return;

		visitReturnValue(statement, scope);
	}

	/* (non-Javadoc)
	 * @see org.evosuite.assertion.AssertionTraceObserver#visit(org.evosuite.testcase.StatementInterface, org.evosuite.testcase.Scope, org.evosuite.testcase.VariableReference)
	 */
	/** {@inheritDoc} */
	@Override
	protected void visit(Statement statement, Scope scope, VariableReference var) {
//		logger.debug("Checking for args value of " );
		try {
//			if (statement.getNumParameters() == 0)
//				return;

			if (!(statement instanceof ConstructorStatement) && !(statement instanceof MethodStatement)) {
				return;
			}
			
			EntityWithParametersStatement entityWithParams = (EntityWithParametersStatement) statement;

			List<VariableReference> parameters = entityWithParams.getParameterReferences();
			List<Object> values = new ArrayList<>();
			
			for (VariableReference param : parameters) {
				Object object = param.getObject(scope);
				values.add(object);
			}
//			Object object = var.getObject(scope);
			trace.addEntry(statement.getPosition(), var, new ArgumentValueTraceEntry(parameters, values));
//			logger.warn("added arg value!");
		} catch (CodeUnderTestException e) {
			logger.debug("", e);
			logger.warn("Unable to trace argument value");
			e.printStackTrace();
			//throw new UnsupportedOperationException();
		}
	}

	@Override
	public void testExecutionFinished(ExecutionResult r, Scope s) {
		// do nothing
	}
}