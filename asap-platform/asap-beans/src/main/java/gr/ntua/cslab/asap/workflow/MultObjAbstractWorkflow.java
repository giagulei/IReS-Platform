//package gr.ntua.cslab.asap.workflow;
//
//import gr.ntua.cslab.asap.operators.Operator;
//import gr.ntua.cslab.asap.staticLibraries.MaterializedWorkflowLibrary;
//import gr.ntua.cslab.asap.staticLibraries.OperatorLibrary;
//
//import java.util.*;
//import java.util.logging.Logger;
//
//import org.apache.commons.collections.map.HashedMap;
//import org.moeaframework.Executor;
//import org.moeaframework.core.NondominatedPopulation;
//import org.moeaframework.core.Solution;
//import org.moeaframework.core.variable.EncodingUtils;
//
//
///**
// * Created by giagulei on 10/7/2017.
// */
//public class MultObjAbstractWorkflow extends AbstractWorkflow1{
//
//    // target metric functions. Key: function name, Value: Optimization function
//    // example:
//    //      key: execTime, value: min
//    public HashMap<String, String> optimizationFunctions;
//
//    private static Logger logger = Logger.getLogger(MultObjAbstractWorkflow.class.getName());
//
//    public MultObjAbstractWorkflow(String name) {
//        super(name);
//    }
//
//    public MultObjAbstractWorkflow(String name, String directory){
//        super(name, directory);
//        logger.info("Ready to initialize");
//        optimizationFunctions = new HashMap<>();
//    }
//
//    public int indx = 0;
//
//    public void parsePolicy(String policy) {
//        this.policy=policy;
//        groupInputs = new HashMap<>();
//        String[] p = policy.split("\n");
//        for (int i = 0; i < p.length; i++) {
//            String[] p1 = p[i].split(",");
//            if(p1[0].equals("groupInputs")){
//                groupInputs.put(p1[1], p1[2]);
//            }
//            else if(p1[0].equals("function")){
//                optimizationFunctions.put(p1[1], p1[2]);
//            }
//        }
//    }
//
//    public MaterializedWorkflow1 materialize(String nameExtention, String policy) throws Exception {
//
//        logger.info("POUTSES2");
//A
//        OperatorLibrary.moveid = 0;
//        parsePolicy(policy);
//        MaterializedWorkflow1 materializedWorkflow = null;
//
//        if(optimizationFunctions.size() <= 1){
//            logger.info("Single objective optimization");
//            materializedWorkflow = super.materialize(nameExtention, policy);
//        }else{
//            logger.info("Multi objective optimization");
//
//            String fullName=name+"_"+nameExtention;
//            materializedWorkflow = new MaterializedWorkflow1(fullName,
//                    MaterializedWorkflowLibrary.getWorkflowDirectory()+"/"+fullName);
//
//            materializedWorkflow.count = this.count;
//
//            if(materilizedDatasets != null)A
//                materializedWorkflow.materilizedDatasets = materilizedDatasets;
//            else
//                materializedWorkflow.materilizedDatasets = new HashMap<>();
//
//            materializedWorkflow.setAbstractWorkflow(this);
//            materializedWorkflow.setPolicy(groupInputs, optimizationFunctions);
//
//            //==================================================================
//            logger.info("Test what we have.");
//
//            logger.info("Print targets: ");
//            for(WorkflowNode t : targets){
//                logger.info(t.getName());
//            }
//
//            // At first there is no materialized datasets
//            logger.info("Print materialized datasets: ");
//            for(Map.Entry<String, WorkflowNode> e : materializedWorkflow.materilizedDatasets.entrySet()){
//                logger.info(e.getKey()+" "+e.getValue().dataset.datasetName);
//            }
//
//            // workflownodes are the nodes of the abstract workflow including both operators and datasets.
//            logger.info("Print abstract workflow:");
//            for(Map.Entry<String, WorkflowNode> e : workflowNodes.entrySet()){
//                logger.info(e.getKey()+" "+e.getValue().getName());
//            }
//            //==================================================================
//
//            logger.info("Constructing operators graph: ");
//            // Workflownodes are stored in a hashmap and there is no ordering.
//            // For reconstructing the operator graph, I have to start from the targets.
//
//            Map<Integer, WorkflowNode> operators = findOperators(workflowNodes);
//
////            TreeMap<Integer, WorkflowNode> moeaOperatorGraph = new TreeMap<>();
////
////            populateMOEAGraph(targets.get(0), moeaOperatorGraph);
////
////
////
////
////            //====== test print code ===================
////            for(Map.Entry<Integer, WorkflowNode> e : moeaOperatorGraph.entrySet()){
////                logger.info(e.getKey()+" ==> "+e.getValue().getName());
////                logger.info(e.getValue().getName()+" parents: ");
////                for(Integer child : e.getValue().parents){
////                    logger.info(moeaOperatorGraph.get(child).getName());
////                }
////            }
////            //==========================================
//
//            int metricIndex = 0;
//            HashMap<String, Integer> objectives = new HashMap<>();
//            for(String objectiveMetric : optimizationFunctions.keySet()){
//                objectives.put(objectiveMetric, metricIndex);
//                metricIndex++;
//            }
//
//            NSGAIIPlanning.operators = operators;
//            NSGAIIPlanning.abstractWorkflow = workflowNodes;
//            NSGAIIPlanning.targets = targets;
//            NSGAIIPlanning.objectives = objectives;
//
//            //NSGAIIPlanning.initialNodes = moeaOperatorGraph;
//
//            NSGAIIPlanning.inverseMaterializedOperators = new HashMap<>();
//            try {
//                NSGAIIPlanning.findMaterializedOperators();
//            } catch (Exception e) {
//                logger.info("Error when tries to find materialized operators");
//                e.printStackTrace();
//            }
//            NSGAIIPlanning.optimizationFunctions = optimizationFunctions;
//
//
//            int times = 10;
//            NondominatedPopulation result = new Executor()
//                    .withProblemClass(NSGAIIPlanning.class)
//                    .withAlgorithm("NSGAII")
//                    .withProperty("populationSize", 5) //max pareto plans
//                    .withMaxEvaluations(times)
//                    .run();
//
//            //============== Print all discovered Pareto plans ============================================
//
//            for (int i = 0; i < result.size(); i++) {
//                Solution pareto = result.get(i);
//                int[] mapping = EncodingUtils.getInt(pareto);
//                logger.info("Pareto plan "+i);
//                for(Map.Entry<Integer, WorkflowNode> oper : moeaOperatorGraph.entrySet()){
//                    Operator op = NSGAIIPlanning.materializedOperators.get(mapping[oper.getKey()]);
//                    logger.info(op.getEngine()+" selected for "+moeaOperatorGraph.get(oper.getKey()).getName());
//                }
//            }
//
//            //================ Always return first solution for materialization =============================
//            List<WorkflowNode> bestPlan = new ArrayList<>();
//            Solution pareto = result.get(0);
//            int[] mapping = EncodingUtils.getInt(pareto);
//            for(Map.Entry<Integer, WorkflowNode> oper : moeaOperatorGraph.entrySet()){
//                Operator op = NSGAIIPlanning.materializedOperators.get(mapping[oper.getKey()]);
//                WorkflowNode planNode = new WorkflowNode(true, false,
//                        moeaOperatorGraph.get(oper.getKey()).getAbstractName());
//                planNode.setOperator(op);
//                bestPlan.add(planNode);
//                logger.info("Op "+planNode.operator.opName+" in plan");
//                for(WorkflowNode inp : planNode.inputs){
//                    logger.info("In: "+inp.dataset.datasetName);
//                }
//                for(WorkflowNode inp : planNode.outputs){
//                    logger.info("Out: "+inp.dataset.datasetName);
//                }
//            }
//
//
//            //materializedWorkflow.setBestPlan(targets.get(0).toStringNorecursive(), bestPlan);
//
//
//
//            //==================================================
//            Workflow1DPTable dpTable = new Workflow1DPTable();
//            WorkflowNode temp = null;
//            Double bestCost = 0.0;
//            Double tempCost = 0.0;
//            //List<WorkflowNode> bestPlan=null;
//
////            for(WorkflowNode t : targets){
////                logger.info( "Materializing workflow node: " + t.toStringNorecursive());
////                List<WorkflowNode> l = t.materialize(materializedWorkflow,dpTable,t.getName());
////			/* vpapa: assert that WorkflowNode.materialize() returned something
////				valid
////			*/
////                if( l != null && !l.isEmpty()){
////                    if(functionTarget.contains("min")){
////                        bestCost=Double.MAX_VALUE;
////                        for(WorkflowNode r : l){
////                            tempCost = dpTable.getCost(r.dataset);
////                            if(tempCost<bestCost){
////                                bestCost=tempCost;
////                                bestPlan=dpTable.getPlan(r.dataset);
////                            }
////                        }
////                    }
////                    else if(functionTarget.contains("max")){
////                        bestCost = -Double.MAX_VALUE;
////                        for(WorkflowNode r : l){
////                            tempCost = dpTable.getCost(r.dataset);
////                            if(tempCost>bestCost){
////                                bestCost=tempCost;
////                                bestPlan=dpTable.getPlan(r.dataset);
////                            }
////                        }
////                    }
////				/* vpapa: target may have or may have not a dataset
////				*/
////                    if( t.dataset != null){
////					/* vpapa: temp below is going to be the next WorkflowNode
////						having as materialized dataset the dataset of the current
////						target WorkflowNode and as input the current target itself
////					*/
////                        temp = new WorkflowNode(false, false, t.getName());
////                        temp.setDataset( t.dataset);
////                        //System.out.println(l);
////                        temp.addInputs(l);
////                        materializedWorkflow.addTarget(temp);
////                    }
////                    else{
////					/* vpapa: in the absence of a dataset still the operator should
////						be added to the workflow
////					*/
////                        temp = l.get( 0).inputs.get( 0);
////                        temp.setDataset( l.get( 0).dataset);
////                        materializedWorkflow.addTarget(temp);
////                    }
////                    bestPlan.add(t);
////                    materializedWorkflow.setBestPlan(t.toStringNorecursive(), bestPlan);
////                    logger.info("Optimal cost: "+bestCost);
////                    materializedWorkflow.optimalCost=bestCost;
////                }
////            }
//        }
//
//
//       return materializedWorkflow;
//    }// end of AbstractWorkflow1 materialize
//
//
////    public Set<Integer> populateMOEAGraph(WorkflowNode t, TreeMap<Integer, WorkflowNode> operatorsToPlan){
////        Set<Integer> parents = new HashSet<>();
////        for(WorkflowNode in : t.inputs){
////            parents.addAll(populateMOEAGraph(in, operatorsToPlan));
////        }
////        Set<Integer> returnSet = new HashSet<>();
////        if(t.isOperator) {
////            t.parents = parents;
////            operatorsToPlan.put(indx, t);
////            indx++;
////            returnSet.add(indx-1);
////        }else{
////            returnSet.addAll(parents);
////        }
////        return returnSet;
////    }
//
//    /**
//     * Takes as input the abstract workflow, it indexes the operators of the workflow by assigning a unique ID to each
//     * of them and returns a map of the operators indexed by the assigned IDs
//     *
//     * @param abstractWorkflow The abstract workflow of the job including both datasets and operators
//     * @return a map with the operators of the workflow
//     */
//    public Map<Integer, WorkflowNode> findOperators(HashMap<String,WorkflowNode> abstractWorkflow){
//        Map<Integer, WorkflowNode> operators = new HashedMap();
//        int opIndex = 0;
//        for(Map.Entry<String, WorkflowNode> e : abstractWorkflow.entrySet()){
//            if(e.getValue().isOperator) {
//                e.getValue().setID(opIndex);
//                operators.put(opIndex, e.getValue());
//                opIndex++;
//            }
//        }
//        return operators;
//    }
//
//
//
////    public void printAbstractDAG(WorkflowNode t) throws Exception {
////        logger.info("node: "+t.getName());
////
////        logger.info("Inputs: ");
////        for(WorkflowNode in : t.inputs){
////            logger.info(in.getName());
////            printAbstractDAG(in);
////        }
////    }
////
////    public void generateDAG(WorkflowNode t, List<AbstractOperator> operators){
////
////        if(t.isOperator){
////            operators.add(t.abstractOperator);
////        }
////
////        for(WorkflowNode in : t.inputs){
////            generateDAG(in, operators);
////        }
////    }
//
//
//
//}
