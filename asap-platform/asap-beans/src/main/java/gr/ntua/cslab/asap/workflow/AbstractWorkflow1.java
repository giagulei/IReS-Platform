/*
 * Copyright 2016 ASAP.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package gr.ntua.cslab.asap.workflow;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

//import net.sourceforge.jeval.EvaluationException;
//import net.sourceforge.jeval.Evaluator;
import gr.ntua.cslab.asap.operators.*;
import gr.ntua.cslab.asap.staticLibraries.AbstractOperatorLibrary;
import gr.ntua.cslab.asap.staticLibraries.DatasetLibrary;
import gr.ntua.cslab.asap.staticLibraries.MaterializedWorkflowLibrary;
import gr.ntua.cslab.asap.staticLibraries.OperatorLibrary;
import gr.ntua.cslab.asap.rest.beans.OperatorDictionary;
import gr.ntua.cslab.asap.rest.beans.WorkflowDictionary;
import org.apache.commons.collections.map.HashedMap;
import org.moeaframework.Executor;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.EncodingUtils;

public class AbstractWorkflow1 {
	protected List<WorkflowNode> targets;
	private List<WorkflowNode> abstractInputs;
	public HashMap<String,WorkflowNode> workflowNodes;
	public String name, directory;
	private static Logger logger = Logger.getLogger(AbstractWorkflow1.class.getName());

	protected HashMap<String, WorkflowNode> materilizedDatasets;
	public HashMap<String,String> groupInputs;
	public String optimizationFunction;
	public String functionTarget;
	protected String policy;
	protected int count;

	//===========================
	// gmytil - multiobjective
	// TODO: gia thn wra upo8etw koino function kai den to xrhsimopoiw
	// target metric functions. Key: function name, Value: Optimization function
	// example:
	//      key: execTime, value: min
	public HashMap<String, String> optimizationFunctions;

	@Override
	public String toString() {
		return targets.toString();
	}

	public AbstractWorkflow1(String name) {
		this.directory = "/tmp";
		this.name=name;
		targets = new ArrayList<WorkflowNode>();
		workflowNodes = new HashMap<String, WorkflowNode>();
		materilizedDatasets = new HashMap<String, WorkflowNode>();
		optimizationFunctions = new HashMap<>();
	}

	public AbstractWorkflow1(String name, String directory) {
		this.directory = directory;
		this.name=name;
		targets = new ArrayList<WorkflowNode>();
		workflowNodes = new HashMap<String, WorkflowNode>();
		optimizationFunctions = new HashMap<>();
	}

	public void addTarget(WorkflowNode target) {
		targets.add(target);
	}

	public void addTargets(List<WorkflowNode> targets) {
		this.targets.addAll(targets);
	}

	public List<WorkflowNode> getTargets() {
		return targets;
	}

	public MaterializedWorkflow1 materialize(String nameExtention, String policy) throws Exception {

		OperatorLibrary.moveid = 0;
		parseGeneralPolicy(policy);
		MaterializedWorkflow1 materializedWorkflow = null;


		//=======================================================
		logger.info("Exhaustive materialization...");
		long startExhaustive = System.currentTimeMillis();
		String fullName=name+"_"+nameExtention;
		materializedWorkflow = new MaterializedWorkflow1(fullName,
				MaterializedWorkflowLibrary.getWorkflowDirectory()+"/"+fullName);
		materializedWorkflow.count = this.count;
		if(materilizedDatasets!=null)
			materializedWorkflow.materilizedDatasets=materilizedDatasets;
		else
			materializedWorkflow.materilizedDatasets=new HashMap<>();
		materializedWorkflow.setAbstractWorkflow(this);
		materializedWorkflow.setPolicy(groupInputs, optimizationFunction, functionTarget);
		Workflow1DPTable dpTable = new Workflow1DPTable();
		logger.info("Initiated!!");
		int planCount = 0;
		double minExecTime = Integer.MAX_VALUE;
		for(WorkflowNode t : targets) {
			logger.info("Materializing workflow node: " + t.toStringNorecursive());
			List<WorkflowNode> l = t.materializeExhaustive(materializedWorkflow, dpTable, t.getName());
			logger.info("Candidate Plans: ");
			for(WorkflowNode r : l){
				planCount++;
				logger.info("Plan for dataset: "+r.toStringNorecursive());
				//List<List<WorkflowNode>> candidatePlans = dpTable.eTable.get(r.dataset);
				List<WorkflowNode> cplan = dpTable.getPlan(r.dataset);
				String planString = "";
				for(WorkflowNode w: cplan) planString += w.toStringNorecursive()+" ";
				logger.info(planString);
				HashMap<String, Double> finalMetrics = dpTable.getMetrics(r.dataset);
				String metricsString="";
				for(Entry<String, Double> m : finalMetrics.entrySet()){
					metricsString+=m.getKey()+"  "+m.getValue()+" => ";
					if(m.getKey().contains("exec") && m.getValue() < minExecTime){
						minExecTime = m.getValue();
					}
				}
				logger.info(metricsString);
			}
		}
		long endExhaustive = System.currentTimeMillis();
		logger.info("Exhaustive Time: "+(endExhaustive-startExhaustive)+" milliseconds.");
		logger.info("PLANCOUNT = "+planCount+ " OPTIMAL EXEC TIME = "+minExecTime);
		//=========================================================

		if (optimizationFunctions.size() <= 1) {
			logger.info("Single objective optimization");
			long startDP = System.currentTimeMillis();
			materializedWorkflow = dpMaterialize(nameExtention, policy);
			long endDP = System.currentTimeMillis();
			logger.info("DP Time: "+(endDP-startDP)+" milliseconds.");
		} else {
			long startNSGA = System.currentTimeMillis();
			materializedWorkflow = NSGAIIMaterialize(nameExtention, policy);
			long endNSGA = System.currentTimeMillis();
			logger.info("NSGAII Time: "+(endNSGA-startNSGA)+" milliseconds.");
		}// end of AbstractWorkflow1 materialize
		return materializedWorkflow;
	}

	public MaterializedWorkflow1 NSGAIIMaterialize(String nameExtention, String policy) throws Exception {
		logger.info("Multi objective optimization -- NSGA-II algorithm using MOEA framework");

			/*
			 * NSGA-II is a genetic algorithm, the performance of which is tuned by two parameters:
			 * 				- NUM_GENERATIONS
			 * 				- MAX_PARETO_PLANS
			 */

		int NUM_GENERATIONS = 100;
		int MAX_PARET0_PLANS = 100;

		String fullName = name + "_" + nameExtention;
		MaterializedWorkflow1 materializedWorkflow = new MaterializedWorkflow1(fullName,
				MaterializedWorkflowLibrary.getWorkflowDirectory() + "/" + fullName);

		materializedWorkflow.count = this.count;

		if (materilizedDatasets != null)
			materializedWorkflow.materilizedDatasets = materilizedDatasets;
		else
			materializedWorkflow.materilizedDatasets = new HashMap<>();

		materializedWorkflow.setAbstractWorkflow(this);
		//TODO: kataxrhstika gia twra. 8a prepei na allaksei to policy
		materializedWorkflow.setPolicy(groupInputs, optimizationFunction, functionTarget);

		Map<Integer, WorkflowNode> operators = findOperators(workflowNodes);

		// Initializing NSGA-II evaluation structs for the problem
		NSGAIIPlanning.costMetrics = new HashMap<>();
		NSGAIIPlanning.objectives = new HashMap<>();
		int objectiveIndex = 0;
		for(String metric : optimizationFunctions.keySet()){
			NSGAIIPlanning.objectives.put(metric, objectiveIndex);
			NSGAIIPlanning.costMetrics.put(metric, 0.0);
			objectiveIndex++;
		}
		NSGAIIPlanning.operators = operators;
		NSGAIIPlanning.abstractWorkflow = workflowNodes;
		NSGAIIPlanning.targets = targets;
		NSGAIIPlanning.functionTarget = functionTarget;
		NSGAIIPlanning.materializedWorkflow = materializedWorkflow;
		NSGAIIPlanning.permittedMaterializations = new HashMap<>();
		NSGAIIPlanning.solutionMap = new HashMap<>();

		try {
			NSGAIIPlanning.findMaterializedOperators();
		} catch (Exception e) {
			logger.info("Error when tries to find materialized operators");
			e.printStackTrace();
		}

		NondominatedPopulation result = new Executor()
				.withProblemClass(NSGAIIPlanning.class)
				.withAlgorithm("NSGAII")
				.withProperty("populationSize", MAX_PARET0_PLANS) //max pareto plans
				.withMaxEvaluations(NUM_GENERATIONS)
				.run();

		//============== Print all discovered Pareto plans ============================================

		for (int i = 0; i < result.size(); i++) {
			Solution pareto = result.get(i);

			logger.info("Result solution: "+pareto.toString());

//			int[] mapping = EncodingUtils.getInt(pareto);
//			logger.info("Pareto plan "+i);
//			for(Map.Entry<Integer, WorkflowNode> oper : operators.entrySet()){
//				Operator op = NSGAIIPlanning.materializedOperators.get(mapping[oper.getKey()]);
//				logger.info(op.getEngine()+" selected for "+operators.get(oper.getKey()).getName());
//			}


			logger.info("Plan Cost:");
			for (Entry<String, Double> e : NSGAIIPlanning.solutionMap.get(pareto.toString())
					.optimalCosts.entrySet()) {
				logger.info(e.getKey() + ": " + e.getValue());
			}

		}
		logger.info("END PARETO");
		//=============================================================================================

		//================ Always return first solution for materialization =============================
            Solution pareto = result.get(0);
			materializedWorkflow = NSGAIIPlanning.solutionMap.get(pareto);

		//==== Print cost of optimal plan =============

		logger.info("Final Plan Cost:");
		for(Entry<String, Double> e : materializedWorkflow.optimalCosts.entrySet()){
			logger.info(e.getKey()+": "+e.getValue());
		}



		return  materializedWorkflow;
	}


	public MaterializedWorkflow1 dpMaterialize(String nameExtention, String policy) throws Exception {
		OperatorLibrary.moveid=0;
		parsePolicy(policy);
		String fullName=name+"_"+nameExtention;
		MaterializedWorkflow1 materializedWorkflow = new MaterializedWorkflow1(fullName, MaterializedWorkflowLibrary.getWorkflowDirectory()+"/"+fullName);
		materializedWorkflow.count = this.count;
		if(materilizedDatasets!=null)
			materializedWorkflow.materilizedDatasets=materilizedDatasets;
		else
			materializedWorkflow.materilizedDatasets=new HashMap<>();
		materializedWorkflow.setAbstractWorkflow(this);
		materializedWorkflow.setPolicy(groupInputs, optimizationFunction, functionTarget);

		Workflow1DPTable dpTable = new Workflow1DPTable();
		WorkflowNode temp = null;
		Double bestCost = 0.0;
		Double tempCost = 0.0;
		List<WorkflowNode> bestPlan=null;

		for(WorkflowNode t : targets){
			logger.info( "Materializing workflow node: " + t.toStringNorecursive());
			List<WorkflowNode> l = t.materialize(materializedWorkflow,dpTable,t.getName());
			/* vpapa: assert that WorkflowNode.materialize() returned something
				valid
			*/
			if( l != null && !l.isEmpty()){
				if(functionTarget.contains("min")){
					bestCost=Double.MAX_VALUE;
					for(WorkflowNode r : l){
						tempCost = dpTable.getCost(r.dataset);
						if(tempCost<bestCost){
							bestCost=tempCost;
							bestPlan=dpTable.getPlan(r.dataset);
						}
					}
				}
				else if(functionTarget.contains("max")){
					bestCost = -Double.MAX_VALUE;
					for(WorkflowNode r : l){
						tempCost = dpTable.getCost(r.dataset);
						if(tempCost>bestCost){
							bestCost=tempCost;
							bestPlan=dpTable.getPlan(r.dataset);
						}
					}
				}
				/* vpapa: target may have or may have not a dataset
				*/
				if( t.dataset != null){
					/* vpapa: temp below is going to be the next WorkflowNode
						having as materialized dataset the dataset of the current
						target WorkflowNode and as input the current target itself
					*/
					temp = new WorkflowNode(false, false, t.getName());
					temp.setDataset( t.dataset);
					//System.out.println(l);
					temp.addInputs(l);
					materializedWorkflow.addTarget(temp);
				}
				else{
					/* vpapa: in the absence of a dataset still the operator should
						be added to the workflow
					*/
					temp = l.get( 0).inputs.get( 0);
					temp.setDataset( l.get( 0).dataset);
					materializedWorkflow.addTarget(temp);
				}
				bestPlan.add(t);
				materializedWorkflow.setBestPlan(t.toStringNorecursive(), bestPlan);
				logger.info("Optimal cost: "+bestCost);
				materializedWorkflow.optimalCost=bestCost;
			}
		}

		return materializedWorkflow;
	}// end of AbstractWorkflow1 materialize



	public MaterializedWorkflow1 replan( HashMap<String, WorkflowNode> materilizedDatasets, int count) throws Exception {
		this.materilizedDatasets=materilizedDatasets;
		this.count =count;
		MaterializedWorkflow1 ret = materialize("", policy);
		this.materilizedDatasets=null;
		this.count = 0;
		return ret;
	}

	private String parsePolicy(String policy) {
		this.policy=policy;
		groupInputs = new HashMap<String, String>();
		String[] p = policy.split("\n");
		for (int i = 0; i < p.length; i++) {
			String[] p1 = p[i].split(",");
			if(p1[0].equals("groupInputs")){
				groupInputs.put(p1[1], p1[2]);
			}
			else if(p1[0].equals("function")){
				optimizationFunction=p1[1];
				functionTarget=p1[2];
			}
		}
		return optimizationFunction;
		//System.out.println(functionTarget);
	}

	public void parseGeneralPolicy(String policy) {
		this.policy=policy;
		groupInputs = new HashMap<>();
		String[] p = policy.split("\n");
		for (int i = 0; i < p.length; i++) {
			String[] p1 = p[i].split(",");
			if(p1[0].equals("groupInputs")){
				groupInputs.put(p1[1], p1[2]);
			}
			else if(p1[0].equals("function")){
				//TODO: kataxrhstika gia twra. genika to functionTarget kai to optimizationFunction den exei logo na meinei edw
				optimizationFunction=p1[1];
				functionTarget=p1[2];
				optimizationFunctions.put(p1[1], p1[2]);
			}
		}
	}

	public static String getPolicyFromString(String policy) {
		String[] p = policy.split("\n");
		for (int i = 0; i < p.length; i++) {
			String[] p1 = p[i].split(",");
			if(p1[0].equals("function")){
				return p1[1];
			}
		}
		return "";
		//System.out.println(functionTarget);
	}

	public void writeToDir(String directory) throws Exception {

		for(WorkflowNode t : targets){
			t.setAllNotVisited();
		}

        File workflowDir = new File(directory);
        if (!workflowDir.exists()) {
        	workflowDir.mkdirs();
        }
        File opDir = new File(directory+"/operators");
        if (!opDir.exists()) {
        	opDir.mkdirs();
        }
        File datasetDir = new File(directory+"/datasets");
        if (!datasetDir.exists()) {
        	datasetDir.mkdirs();
        }
        File edgeGraph = new File(directory+"/graph");
    	FileOutputStream fos = new FileOutputStream(edgeGraph);

    	BufferedWriter graphWritter = new BufferedWriter(new OutputStreamWriter(fos));


		for(WorkflowNode t : targets){
			t.writeToDir(directory+"/operators",directory+"/datasets",graphWritter);
			graphWritter.write(t.toStringNorecursive() +",$$target");
			graphWritter.newLine();
		}

		graphWritter.close();
	}


	public void readFromDir(String directory) throws IOException {
		HashMap<String,WorkflowNode> nodes = new HashMap<String, WorkflowNode>();
		File folder = new File(directory+"/operators");
		File[] files = folder.listFiles();

		for (int i = 0; i < files.length; i++) {
			if (files[i].isFile() && !files[i].isHidden()) {
				WorkflowNode n = new WorkflowNode(true, true,"");
				AbstractOperator temp = new AbstractOperator(files[i].getName());
				temp.readPropertiesFromFile(files[i]);
				n.setAbstractOperator(temp);
				nodes.put(temp.opName, n);
			}
		}
		folder = new File(directory+"/datasets");
		/* vpapa: read only if datasets folder exists and it has content */
		if( folder.exists()){
			files = folder.listFiles();
			if( files != null && files.length > 0){
				for (int i = 0; i < files.length; i++) {
					if (files[i].isFile() && !files[i].isHidden()) {
						WorkflowNode n =null;
						Dataset temp = new Dataset(files[i].getName());
						temp.readPropertiesFromFile(files[i]);
						int metadatsize =temp.datasetTree.tree.size();
						if(metadatsize==0){
							n = new WorkflowNode(false, true,"");
						}
						else{
							n = new WorkflowNode(false, false, "");
						}
						n.setDataset(temp);
						nodes.put(temp.datasetName, n);
					}
				}
			}
		}
		//putting nodes into workflowNodes make them available for printing at IReS WUI
		workflowNodes.putAll(nodes);
		File edgeGraph = new File(directory+"/graph");
		FileInputStream fis = new FileInputStream(edgeGraph);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));

		String line = null;
		String[] e	= null;
		WorkflowNode src = null;
		WorkflowNode dest = null;
		/* vpapa: operators or datasets defined in graph file may be missing from
			the corresponding folders or misswritten into abstract workflow's graph
			file
		*/
		try{
			while ((line = br.readLine()) != null) {
				e = line.split(",");
				if(e[1].equals("$$target")){
					this.targets.add(nodes.get(e[0]));
				}
				else if(e.length==2){
					src = nodes.get(e[0]);
					dest = nodes.get(e[1]);
					dest.addInput(src);
					src.addOutput(dest);
				}
				else if(e.length==3){
					src = nodes.get(e[0]);
					dest = nodes.get(e[1]);
					if(dest.isOperator){
						dest.addInput(Integer.parseInt(e[2]), src);
						src.addOutput(dest);
					}
					else{
						dest.addInput(src);
						src.addOutput(Integer.parseInt(e[2]), dest);
					}
				}
			}
		}
		catch( NullPointerException npe){
			System.out.println( "ERROR: The corresponding files of operators or datasets"
								+ " " + e[ 0] + " and " + e[ 1] + " it looks like"
								+ " that they are missing from the relative folders"
								+ "or miswritten into abstract workflow's graph file.");
			logger.info( "ERROR: The corresponding files of operators or datasets"
								+ " " + e[ 0] + " and " + e[ 1] + " it looks like"
								+ " that they are missing from the relative folders"
								+ "or miswritten into abstract workflow's graph file.");
		}
        catch( IndexOutOfBoundsException iobe){
             System.out.println( "ERROR: Destination: " + dest + " has " + src + "as source"
             					 + " at its input with number " + e[ 2] + ". However, " + dest
             					 + " has " + dest.inputs.size() + " inputs which is less than " + e[ 2] + "."
             					 + " Make sure that the graph file is set appropriately of the"
             					 + " corresponding workflow for this operator i.e. " + dest + ".");
             logger.info( "ERROR: Destination: " + dest + " has " + src + "as source"
 					 + " at its input with number " + e[ 2] + ". However, " + dest
 					 + " has " + dest.inputs.size() + " inputs which is less than " + e[ 2] + "."
 					 + " Make sure that the graph file is set appropriately of the"
 					 + " corresponding workflow for this operator i.e. " + dest + ".");
        }
		br.close();
	}


	public void refresh() {
		for(Entry<String, WorkflowNode> e : workflowNodes.entrySet()){
			if(e.getValue().isOperator){
				AbstractOperator abstractOp = AbstractOperatorLibrary.getOperator(e.getKey());
				workflowNodes.get(e.getKey()).abstractOperator=abstractOp;
			}
			else if(!e.getValue().isAbstract){
				Dataset d = DatasetLibrary.getDataset(e.getKey());
				workflowNodes.get(e.getKey()).dataset=d;
			}
		}

	}


	public String graphToString() throws IOException {
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		BufferedWriter graphWritter = new BufferedWriter(new OutputStreamWriter(bs));

		for(WorkflowNode n : workflowNodes.values()){
			n.graphToString(graphWritter);
		}

		for(WorkflowNode t : targets){
			graphWritter.write(t.toStringNorecursive() +",$$target");
			graphWritter.newLine();
		}

		graphWritter.close();
		return bs.toString("UTF-8");
	}

	public String graphToStringRecursive() throws IOException {

		for(WorkflowNode t : targets){
			t.setAllNotVisited();
		}

		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		BufferedWriter graphWritter = new BufferedWriter(new OutputStreamWriter(bs));


		for(WorkflowNode t : targets){
			t.graphToStringRecursive(graphWritter);
			graphWritter.write(t.toStringNorecursive() +",$$target");
			graphWritter.newLine();
		}

		graphWritter.close();
		return bs.toString("UTF-8");
	}


	public void changeEdges(String workflowGraph) throws IOException {
		targets= new ArrayList<WorkflowNode>();
		for(WorkflowNode n : workflowNodes.values()){
			n.inputs = new ArrayList<WorkflowNode>();
		}
		ByteArrayInputStream fis = new ByteArrayInputStream(workflowGraph.getBytes());
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));

		String line = null;
		while ((line = br.readLine()) != null) {
			String[] e =line.split(",");
			if(e[1].equals("$$target")){
				this.targets.add(workflowNodes.get(e[0]));
			}

			else if(e.length==2){
				WorkflowNode src = workflowNodes.get(e[0]);
				WorkflowNode dest = workflowNodes.get(e[1]);
				dest.addInput(src);
				src.addOutput(dest);
			}
			else if(e.length==3){
				WorkflowNode src = workflowNodes.get(e[0]);
				WorkflowNode dest = workflowNodes.get(e[1]);
				if(dest.isOperator){
					dest.addInput(Integer.parseInt(e[2]), src);
					src.addOutput(dest);
				}
				else{
					dest.addInput(src);
					src.addOutput(Integer.parseInt(e[2]), dest);
				}
			}
		}
		br.close();
	}

	public void addNode(String type, String name) {
		int t = Integer.parseInt(type);
		WorkflowNode n =null;
		switch (t) {
		case 1:
			n = new WorkflowNode(true, true,"");
			n.setAbstractOperator(AbstractOperatorLibrary.getOperator(name));
			break;
		case 2:
			n = new WorkflowNode(true, false,"");
			n.setOperator(OperatorLibrary.getOperator(name));
			break;
		case 3:
			n = new WorkflowNode(false, true,"");
			n.setDataset(new Dataset(name));
			break;
		case 4:
			n = new WorkflowNode(false, false,"");
			n.setDataset(DatasetLibrary.getDataset(name));

			break;

		default:
			n = new WorkflowNode(false, false,"");
			break;
		}

		workflowNodes.put(name,n);

	}

	public void removeNode(String type, String name) {
		WorkflowNode n = workflowNodes.remove(name);
		for(WorkflowNode n1 : workflowNodes.values()){
			List<WorkflowNode> l =  new ArrayList<WorkflowNode>( n1.inputs);
			int index = 0;
			for(WorkflowNode n2 :l){
				if(n2.compareTo(n)==0){
					n1.inputs.remove(index);
				}
				index++;
			}
		}
	}

	public WorkflowDictionary toWorkflowDictionary(String delimiter) throws Exception {
		WorkflowDictionary ret = new WorkflowDictionary();
		logger.info( "WorkflowNodes: " + workflowNodes);
		logger.info( "WorkflowNodes: " + workflowNodes.values());
		/* vpapa: check for missing operators found in the workflow folder locally or in GitHub
			and note them with a "MISSING" statement
			TODO
		*/
		for(WorkflowNode n : workflowNodes.values()){
			OperatorDictionary op = new OperatorDictionary(n.getAbstractName(), n.toStringNorecursive(), n.getCost()+"", n.getExecTime()+"",
	    			n.getStatus(new HashMap<String, List<WorkflowNode>>()), n.isOperator+"", n.isAbstract+"", n.toKeyValueString(delimiter), targets.contains(n));

			for(WorkflowNode in : n.inputs){
				op.addInput(in.toStringNorecursive());
			}
	    	ret.addOperator(op);
		}
		return ret;
	}

	public WorkflowDictionary toWorkflowDictionaryRecursive(String delimiter) throws Exception {
		for(WorkflowNode t : targets){
			t.setAllNotVisited();
		}
		WorkflowDictionary ret = new WorkflowDictionary();
    	for(WorkflowNode target : targets){
    		target.toWorkflowDictionary(ret, new HashMap<String, List<WorkflowNode>>(), delimiter, targets);
    	}
		return ret;
	}

	/**
     * Takes as input the abstract workflow, it indexes the operators of the workflow by assigning a unique ID to each
     * of them and returns a map of the operators indexed by the assigned IDs
     *
     * @param abstractWorkflow The abstract workflow of the job including both datasets and operators
     * @return a map with the operators of the workflow
     */
    public Map<Integer, WorkflowNode> findOperators(HashMap<String,WorkflowNode> abstractWorkflow){
        Map<Integer, WorkflowNode> operators = new HashedMap();
        int opIndex = 0;
        for(Map.Entry<String, WorkflowNode> e : abstractWorkflow.entrySet()){
            if(e.getValue().isOperator) {
                e.getValue().setID(opIndex);
                operators.put(opIndex, e.getValue());
                opIndex++;
            }
        }
        return operators;
    }



	public static void main(String[] args) throws Exception{


		MaterializedOperators library =  new MaterializedOperators();
		AbstractWorkflow1 abstractWorkflow = new AbstractWorkflow1("test");
		Dataset d1 = new Dataset("hbaseDataset");
		d1.add("Constraints.DataInfo.Attributes.number","2");
		d1.add("Constraints.DataInfo.Attributes.Atr1.type","ByteWritable");
		d1.add("Constraints.DataInfo.Attributes.Atr2.type","List<ByteWritable>");
		d1.add("Constraints.Engine.DB.NoSQL.HBase.key","Atr1");
		d1.add("Constraints.Engine.DB.NoSQL.HBase.value","Atr2");
		d1.add("Constraints.Engine.DB.NoSQL.HBase.location","127.0.0.1");
		d1.add("Optimization.size","1TB");
		d1.add("Optimization.uniqueKeys","1.3 billion");

		WorkflowNode t1 = new WorkflowNode(false,false,"");
		t1.setDataset(d1);
		//d1.writeToPropertiesFile(d1.datasetName);

		Dataset d2 = new Dataset("mySQLDataset");
		d2.add("Constraints.DataInfo.Attributes.number","2");
		d2.add("Constraints.DataInfo.Attributes.Atr1.type","Varchar");
		d2.add("Constraints.DataInfo.Attributes.Atr2.type","Varchar");
		d2.add("Constraints.Engine.DB.Relational.MySQL.schema","...");
		d2.add("Constraints.Engine.DB.Relational.MySQL.location","127.0.0.1");
		d2.add("Optimization.size","1GB");
		d2.add("Optimization.uniqueKeys","1 million");

		WorkflowNode t2 = new WorkflowNode(false,false,"");
		t2.setDataset(d2);
		//d2.writeToPropertiesFile(d2.datasetName);

		AbstractOperator abstractOp = new AbstractOperator("JoinOp");
		abstractOp.add("Constraints.Input.number","2");
		abstractOp.add("Constraints.Output.number","1");
		abstractOp.add("Constraints.Input0.DataInfo.Attributes.number","2");
		abstractOp.add("Constraints.Input1.DataInfo.Attributes.number","2");
		abstractOp.add("Constraints.Output0.DataInfo.Attributes.number","2");
		abstractOp.addRegex(new NodeName("Constraints.OpSpecification.Algorithm.Join", new NodeName(".*", null, true), false), ".*");

		WorkflowNode op1 = new WorkflowNode(true,true,"");
		op1.setAbstractOperator(abstractOp);
		//abstractOp.writeToPropertiesFile(abstractOp.opName);

		AbstractOperator abstractOp1 = new AbstractOperator("SortOp");
		abstractOp1.add("Constraints.Input.number","1");
		abstractOp1.add("Constraints.Output.number","1");
		abstractOp1.add("Constraints.Input0.DataInfo.Attributes.number","2");
		abstractOp1.add("Constraints.Output0.DataInfo.Attributes.number","2");
		abstractOp1.addRegex(new NodeName("Constraints.OpSpecification.Algorithm.Sort", new NodeName(".*", null, true), false), ".*");

		WorkflowNode op2 = new WorkflowNode(true,true,"");
		op2.setAbstractOperator(abstractOp1);
		//abstractOp1.writeToPropertiesFile(abstractOp1.opName);

		Dataset d3 = new Dataset("d3");
		WorkflowNode t3 = new WorkflowNode(false,true,"");
		t3.setDataset(d3);
		Dataset d4 = new Dataset("d4");
		WorkflowNode t4 = new WorkflowNode(false,true,"");
		t4.setDataset(d4);

		op1.addInput(t1);
		op1.addInput(t2);
		t3.addInput(op1);
		op2.addInput(t3);
		t4.addInput(op2);
		abstractWorkflow.addTarget(t4);

		abstractWorkflow.writeToDir("asapLibrary/abstractWorkflows/DataAnalytics");
		System.exit(0);
		MaterializedWorkflow1 mw = abstractWorkflow.materialize("t","");
		System.out.println(abstractWorkflow);
		System.out.println(mw);
		mw.printNodes();

	}
}
