package gr.ntua.cslab.asap.daemon.rest;

import java.io.IOException;

import gr.ntua.cslab.asap.rest.beans.OperatorDictionary;
import gr.ntua.cslab.asap.rest.beans.WorkflowDictionary;
import gr.ntua.cslab.asap.workflow.AbstractWorkflow1;

public class TransformWorkflows {

	public static AbstractWorkflow1 tranformAbstractWorkflow(String name, String dir, WorkflowDictionary workflow) throws IOException {
		

		AbstractWorkflow1 ret = new AbstractWorkflow1(name, dir+"/"+name);
		String graph="", targets="";
		for(OperatorDictionary op: workflow.getOperators()){
			if(op.getIsOperator().equals("true") && op.getIsAbstract().equals("true"))
				ret.addNode("1", op.getName());
			else if(op.getIsOperator().equals("true") && op.getIsAbstract().equals("false"))
				ret.addNode("2", op.getName());
			else if(op.getIsOperator().equals("false") && op.getIsAbstract().equals("true"))
				ret.addNode("3", op.getName());
			else if(op.getIsOperator().equals("false") && op.getIsAbstract().equals("false"))
				ret.addNode("4", op.getName());
			
			for(String in: op.getInput()){
				graph+=in+","+op.getName()+"\n";
			}	
			if(op.isTarget())
				targets+=op.getName()+",$$target\n";
		}
		graph+=targets;
		ret.changeEdges(graph);
		return ret;
	}

}