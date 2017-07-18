package gr.ntua.cslab.asap.workflow;

import gr.ntua.cslab.asap.operators.Operator;
import gr.ntua.cslab.asap.staticLibraries.MaterializedWorkflowLibrary;
import gr.ntua.cslab.asap.staticLibraries.OperatorLibrary;

import java.util.*;
import java.util.logging.Logger;

import org.moeaframework.Executor;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.EncodingUtils;


/**
 * Created by giagulei on 10/7/2017.
 */
public class MultObjAbstractWorkflow extends AbstractWorkflow1{

    public HashMap<String, String> optimizationFunctions;

    private static Logger logger = Logger.getLogger(MultObjAbstractWorkflow.class.getName());

    public MultObjAbstractWorkflow(String name) {
        super(name);
    }

    public MultObjAbstractWorkflow(String name, String directory){
        super(name, directory);
        logger.info("Ready to initialize");
        optimizationFunctions = new HashMap<>();
    }

    public int indx = 0;

    public void parsePolicy(String policy) {
        this.policy=policy;
        groupInputs = new HashMap<>();
        String[] p = policy.split("\n");
        for (int i = 0; i < p.length; i++) {
            String[] p1 = p[i].split(",");
            if(p1[0].equals("groupInputs")){
                groupInputs.put(p1[1], p1[2]);
            }
            else if(p1[0].equals("function")){
                optimizationFunctions.put(p1[1], p1[2]);
            }
        }
    }

    public MObjMaterializedWorkflow materialize(String nameExtention, String policy) throws Exception {

        OperatorLibrary.moveid = 0;
        parsePolicy(policy);
        String fullName=name+"_"+nameExtention;
        MObjMaterializedWorkflow materializedWorkflow = new MObjMaterializedWorkflow(fullName,
                MaterializedWorkflowLibrary.getWorkflowDirectory()+"/"+fullName);

        materializedWorkflow.count = this.count;

        if(materilizedDatasets!=null)
            materializedWorkflow.materilizedDatasets=materilizedDatasets;
        else
            materializedWorkflow.materilizedDatasets=new HashMap<>();

        materializedWorkflow.setAbstractWorkflow(this);
        materializedWorkflow.setPolicy(groupInputs, optimizationFunctions);

        TreeMap<Integer, WorkflowNode> moeaOperatorGraph = new TreeMap<>();
        //TODO: assume for now that only one target exists
        populateMOEAGraph(targets.get(0), moeaOperatorGraph);

        //====== test print code ===================
        for(Map.Entry<Integer, WorkflowNode> e : moeaOperatorGraph.entrySet()){
            logger.info(e.getKey()+" ==> "+e.getValue().getName());
            logger.info(e.getValue().getName()+" parents: ");
            for(Integer child : e.getValue().parents){
                logger.info(moeaOperatorGraph.get(child).getName());
            }
        }
        //==========================================

        int metricIndex = 0;
        HashMap<String, Integer> objectives = new HashMap<>();
        for(String objectiveMetric : optimizationFunctions.keySet()){
            objectives.put(objectiveMetric, metricIndex);
            metricIndex++;
        }

        MultiObjectivePlanning.objectives = objectives;
        MultiObjectivePlanning.initialNodes = moeaOperatorGraph;
        try {
            MultiObjectivePlanning.findMaterializedOperators();
        } catch (Exception e) {
            logger.info("Error when tries to find materialized operators");
            e.printStackTrace();
        }
        MultiObjectivePlanning.optimizationFunctions = optimizationFunctions;
        int times = 10;
        NondominatedPopulation result = new Executor()
                                         .withProblemClass(MultiObjectivePlanning.class)
                                         .withAlgorithm("NSGAII")
                                         .withProperty("populationSize", 5) //max pareto plans
                                         .withMaxEvaluations(times)
                                         .run();

        //============== Print all discovered Pareto plans ============================================

        for (int i = 0; i < result.size(); i++) {
            Solution pareto = result.get(i);
            int[] mapping = EncodingUtils.getInt(pareto);
            logger.info("Pareto plan "+i);
            for(Map.Entry<Integer, WorkflowNode> oper : moeaOperatorGraph.entrySet()){
                Operator op = MultiObjectivePlanning.materializedOperators.get(mapping[oper.getKey()]);
                logger.info(op.getEngine()+" selected for "+moeaOperatorGraph.get(oper.getKey()).getName());
            }
        }

        //================ Always return first solution for materialization =============================
        List<WorkflowNode> bestPlan = new ArrayList<>();
        Solution pareto = result.get(0);
        int[] mapping = EncodingUtils.getInt(pareto);
        for(Map.Entry<Integer, WorkflowNode> oper : moeaOperatorGraph.entrySet()){
            Operator op = MultiObjectivePlanning.materializedOperators.get(mapping[oper.getKey()]);
            WorkflowNode planNode = new WorkflowNode(true, false,
                    moeaOperatorGraph.get(oper.getKey()).getAbstractName());
            planNode.setOperator(op);
            bestPlan.add(planNode);
        }

        materializedWorkflow.setBestPlan(targets.get(0).toStringNorecursive(), bestPlan);


        return materializedWorkflow;
    }// end of AbstractWorkflow1 materialize


    public Set<Integer> populateMOEAGraph(WorkflowNode t, TreeMap<Integer, WorkflowNode> operatorsToPlan){
        Set<Integer> parents = new HashSet<>();
        for(WorkflowNode in : t.inputs){
            parents.addAll(populateMOEAGraph(in, operatorsToPlan));
        }
        Set<Integer> returnSet = new HashSet<>();
        if(t.isOperator) {
            t.parents = parents;
            operatorsToPlan.put(indx, t);
            indx++;
            returnSet.add(indx-1);
        }else{
            returnSet.addAll(parents);
        }
        return returnSet;
    }

//    public void printAbstractDAG(WorkflowNode t) throws Exception {
//        logger.info("node: "+t.getName());
//
//        logger.info("Inputs: ");
//        for(WorkflowNode in : t.inputs){
//            logger.info(in.getName());
//            printAbstractDAG(in);
//        }
//    }
//
//    public void generateDAG(WorkflowNode t, List<AbstractOperator> operators){
//
//        if(t.isOperator){
//            operators.add(t.abstractOperator);
//        }
//
//        for(WorkflowNode in : t.inputs){
//            generateDAG(in, operators);
//        }
//    }



}
