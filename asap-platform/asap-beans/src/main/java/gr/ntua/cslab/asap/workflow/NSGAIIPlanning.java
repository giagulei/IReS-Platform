package gr.ntua.cslab.asap.workflow;

import gr.ntua.cslab.asap.operators.Operator;
import gr.ntua.cslab.asap.staticLibraries.ClusterStatusLibrary;
import gr.ntua.cslab.asap.staticLibraries.OperatorLibrary;
import org.apache.log4j.Logger;
import org.moeaframework.core.Solution;
import org.moeaframework.problem.AbstractProblem;
import org.moeaframework.core.variable.EncodingUtils;

import java.io.Serializable;
import java.util.*;

/**
 * Created by giagulei on 11/7/2017.
 */
public class NSGAIIPlanning extends AbstractProblem {


    public static Map<Integer, WorkflowNode> operators;
    public static HashMap<String, WorkflowNode> abstractWorkflow;
    public static List<WorkflowNode> targets;
    public static MaterializedWorkflow1 materializedWorkflow;
    public static String functionTarget;
    public static HashMap<String, Integer> objectives;
    public static HashMap<String, Double> costMetrics;

    public static HashMap<String, MaterializedWorkflow1> solutionMap;

//    public static String solutionIndex(Solution pareto){
//        String index = "";
//        for(Map.Entry<String, Serializable> e: pareto.getAttributes().entrySet()){
//            index += e.getValue().toString();
//        }
//
//        for(Double d : pareto.getConstraints()) index += d.toString();
//        return index;
//    }

    //TODO:
    //public static HashMap<String, String> optimizationFunctions;

    public static HashMap<Integer, Operator> materializedOperators;
    public static HashMap<Integer, List<Integer>> permittedMaterializations;

    private WorkflowNode target; //TODO: make it List<WorkflowNode> targets

    private static Logger logger = Logger.getLogger(NSGAIIPlanning.class.getName());

    public NSGAIIPlanning() {
        super(operators.size(), objectives.size());
    }

    public static void findMaterializedOperators() throws Exception {
        materializedOperators = new HashMap<>();
        int mOperatorsIndex = 0;
        for (java.util.Map.Entry<Integer, WorkflowNode> abstrOp : operators.entrySet()) {
            List<Operator> operators = OperatorLibrary.getMatches(abstrOp.getValue().abstractOperator);
            logger.info("For abstract operator " + abstrOp.getValue().abstractOperator.opName + " the available"
                    + " operator implementations are:\n " + operators);

            for (Operator op : operators) {
                if (!ClusterStatusLibrary.checkEngineStatus(op)) {
                    logger.info("Specified engine for operator " + abstrOp.getValue().abstractOperator.opName
                            + " is " + op.getEngine());
                    logger.info("and it is not running. For this, this operator will not be materialized");
                    logger.info("and consequently the corresponding workflow will not be materialized");
                    logger.info("if alternatives do not exist for the relative abstract operator.");
                    continue;
                } else {
                    materializedOperators.put(mOperatorsIndex, op);
                    if (permittedMaterializations.containsKey(abstrOp.getKey())) {
                        permittedMaterializations.get(abstrOp.getKey()).add(mOperatorsIndex);
                    } else {
                        List<Integer> materList = new ArrayList<>();
                        materList.add(mOperatorsIndex);
                        permittedMaterializations.put(abstrOp.getKey(), materList);
                    }
                    mOperatorsIndex++;
                }
            }
        }
    }



    @Override
    public void evaluate(Solution solution) {
        logger.info("MOEA evaluation");

        int[] mapping = EncodingUtils.getInt(solution);

        // den einai dpTable. kataxrhstika to onoma.
        // xreiazomai mia domh gia na fulaw plans/metrics sthn anadromh.
        // h domh auth mporei na einai idia me to DPTable, alla praktika to table na einai vector kai na exw ena mono
        // entry per dataset.
        Workflow1DPTable mTable = new Workflow1DPTable();
        MaterializedWorkflow1 materialization = materializedWorkflow.clone();

        WorkflowNode temp = null;
        List<WorkflowNode> bestPlan = null;
        List<HashMap<String, Double>> targetMetrics = new ArrayList<>();
        for (WorkflowNode t : targets) {

            logger.info("Materializing workflow node: " + t.toStringNorecursive());
            List<WorkflowNode> l = null;
            try {
                l = t.materializeNSGAII(materialization, mTable, t.getName(), mapping);

                if (l != null && !l.isEmpty()) {

                    WorkflowNode r = l.get(0); // logika perimenw na exei 1 olo k olo
                    bestPlan = mTable.getPlan(r.dataset);

                    targetMetrics.add(mTable.getMetrics(r.dataset));

                    if (t.dataset != null) {
                        temp = new WorkflowNode(false, false, t.getName());
                        temp.setDataset(t.dataset);
                        temp.addInputs(l);
                        materialization.addTarget(temp);
                    } else {
                        temp = l.get(0).inputs.get(0);
                        temp.setDataset(l.get(0).dataset);
                        materialization.addTarget(temp);
                    }
                    bestPlan.add(t);
                    materialization.setBestPlan(t.toStringNorecursive(), bestPlan);

                    // edw prepei na travaw kapws ta optimal metrics
                }else{
                    // if null, there was not a correct match for machine and the cost is maximal
                    for(Map.Entry<String, Double> objScore : costMetrics.entrySet()) {
                        solution.setObjective(objectives.get(objScore.getKey()), Integer.MAX_VALUE);
                    }
                    return;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // combine cost metrics of different targets
        for(String m : costMetrics.keySet()){
            List<Double> t1 = new ArrayList<Double>();
            for(HashMap<String, Double> h : targetMetrics){
                t1.add(h.get(m));
            }
            String g = materialization.groupInputs.get(m);
            Double operatorInputCost=0.0;
            if(g.contains("min")){
                operatorInputCost=Collections.min(t1);
            }
            else if(g.contains("max")){
                operatorInputCost = Collections.max(t1);
            }
            else if(g.contains("sum")){
                for(Double d : t1){
                    operatorInputCost+=d;
                }
            }
            costMetrics.put(m, operatorInputCost);
        }

        materialization.optimalCosts = costMetrics;

        // 8elw to max apo ta metrics pou exoun ta target datasets.
        for(Map.Entry<String, Double> objScore : costMetrics.entrySet()) {
            solution.setObjective(objectives.get(objScore.getKey()), costMetrics.get(objScore.getKey()));
        }

        solutionMap.put(solution.toString(), materialization.clone());
        logger.info("tested solution: "+solution.toString());
    }



    @Override
    public Solution newSolution () {
        Solution solution = new Solution(operators.size(), objectives.size());
        for (Integer abstractOpIndx : operators.keySet()) {

            List<Integer> l = permittedMaterializations.get(abstractOpIndx);
            solution.setVariable(abstractOpIndx, EncodingUtils.newInt(l.get(0), l.get(l.size() - 1)));
        }
        return solution;
    }

}










//    private HashMap<Integer, Double> initializeCostMetrics(){
//        HashMap<Integer, Double> costMetrics = new HashMap<>();
//        for(Integer objID : objectives.values()){
//            costMetrics.put(objID, 0.0);
//        }
//        return costMetrics;
//    }
//
//    public boolean checkSelectionValidity(Operator generatedOperator, AbstractOperator abstractOperator){
////        logger.info("Generated Op: "+generatedOperator.opName);
////        logger.info("Check if matches with: "+abstractOperator.opName);
//        return generatedOperator.opName.contains(abstractOperator.opName);
//    }
//
//    public HashMap<Integer, Double> combineCosts(HashMap<Integer, Double> score1, HashMap<Integer, Double> score2){
//        for(Map.Entry<String, Integer> obj : objectives.entrySet()){
//            double s1 = score1.get(obj.getValue());
//            double s2 = score2.get(obj.getValue());
//            if(optimizationFunctions.get(obj.getKey()).equals("min")){
//                score1.put(obj.getValue(), Math.max(s1, s2));
//            }else if(optimizationFunctions.get(obj.getKey()).equals("max")){
//                score1.put(obj.getValue(), Math.min(s1, s2));
//            }
//        }
//        return score1;
//    }
//
//    public HashMap<Integer, Double> addCosts(HashMap<Integer, Double> score1, HashMap<Integer, Double> score2){
//        for(Map.Entry<Integer, Double> metricEntry : score1.entrySet()){
//            double score2OldValue = score2.get(metricEntry.getKey());
//            score2.put(metricEntry.getKey(), metricEntry.getValue() + score2OldValue);
//        }
//        return score2;
//    }
//
//
//    public void computeCost(){
//
//    }

















//            // check if there is at least one operator with invalid engine assignment.
//            // If there is such an operator, return a solution with the worst possible cost
//            for(Integer nodeIndex : initialNodes.navigableKeySet()) {
//                Operator op = materializedOperators.get(mapping[nodeIndex]);
//                if(!checkSelectionValidity(op, initialNodes.get(nodeIndex).abstractOperator)){
//                    for(Map.Entry<Integer, Double> objScoreEntry : costMetrics.entrySet()) {
//                        solution.setObjective(objScoreEntry.getKey(), Integer.MAX_VALUE);
//                    }
//                    logger.info("NOT FIT.");
//                    return;
//                }
//            }
//
//            // At this point, the proposed solution is valid and we compute its cost.
//            // materialize the input and output datasets for each operator
//            int count = 0;
//
//            // afou ta sarwnw me ascending keys, prwta 8a dw goneis kai meta paidia
//            for(Integer nodeIndex : initialNodes.navigableKeySet()) {
//
//                logger.info("Node "+materializedOperators.get(mapping[nodeIndex]).opName);
////                for(Integer i : initialNodes.get(nodeIndex).parents){
////                    logger.info("Parent: "+initialNodes.get(i).abstractOperator.opName);
////                }
//
//                HashMap<Integer, Double> operatorExecution = new HashMap<>();
//
//                Operator op = materializedOperators.get(mapping[nodeIndex]);
//                WorkflowNode planNode = new WorkflowNode(true, false,
//                        initialNodes.get(nodeIndex).getAbstractName());
//                planNode.setOperator(op);
//                logger.info(op.getEngine() + "," + op.opName + " selected for " + initialNodes.get(nodeIndex).getName());
//
//                for(WorkflowNode w :initialNodes.get(nodeIndex).inputs) logger.info(w.dataset.datasetName);
//
//
//                // add materialized inputs
//                int inputs = Integer.parseInt(op.getParameter("Constraints.Input.number"));
//                for (int i = 0; i < inputs; i++) {
//                    Dataset tempInput = new Dataset("t" + count);
//                    count++;
//                    tempInput.inputFor(op, i); //copies description from operator and materializes dataset
//                    WorkflowNode tempInputNode = new WorkflowNode(false, false, "");
//                    tempInputNode.setDataset(tempInput);
//                    planNode.addInput(tempInputNode);
//                }
//
//                // add materialized outputs
//                int outN = 0;
//                planNode.outputs = new ArrayList<>();
//                planNode.outputs.addAll(initialNodes.get(nodeIndex).outputs);
//                for (WorkflowNode out : planNode.outputs) {
//                    out.isAbstract = false;
//                    Dataset tempOutput = new Dataset("t" + count);
//                    count++;
//                    logger.info("Call outputFor() for operator: " + op.opName);
//                    logger.info("with tempOutput: " + tempOutput);
//                    logger.info("outN: " + outN);
//                    logger.info("temp.inputs: " + planNode.inputs);
//                    try {
//                        op.outputFor(tempOutput, outN, planNode.inputs);
//                    } catch (Exception e) {
//                        logger.info("ERROR: For operator " + op.opName + " there is a");
//                        logger.info("mismatch between the Constraints.Output and");
//                        logger.info("Execution.Output properties inside its description");
//                        logger.info("file. Or maybe, these properties match between them");
//                        logger.info("but they may have a mismatch with the graph file");
//                        logger.info("of the workflow where this operator belongs, e.g. from");
//                        logger.info("the graph file the operatos has x outputs but in the");
//                        logger.info("description file y outputs where declared.");
//                    }
//                    out.setDataset(tempOutput);
//                    out.addInput(planNode);
//
//                }
//
//                plan.put(nodeIndex, planNode);
//            }
//
//            // check if there are matches
//
////            int count = 0;
////
////            // afou ta sarwnw me ascending keys, prwta 8a dw goneis kai meta paidia
////            for(Integer nodeIndex : initialNodes.navigableKeySet()) {
////
////                HashMap<Integer, Double> operatorExecution = new HashMap<>();
////
////                Operator op = materializedOperators.get(mapping[nodeIndex]);
////                WorkflowNode planNode = new WorkflowNode(true, false,
////                        initialNodes.get(nodeIndex).getAbstractName());
////                planNode.setOperator(op);
////                logger.info(op.getEngine()+","+op.opName+" selected for "+initialNodes.get(nodeIndex).getName());
////                int inputs = Integer.parseInt(op.getParameter("Constraints.Input.number"));
////                for (int i = 0; i < inputs; i++) {
////                    Dataset tempInput = new Dataset("t" + count);
////                    count++;
////                    tempInput.inputFor(op, i); //copies description from operator and materializes dataset
////                    WorkflowNode tempInputNode = new WorkflowNode(false, false, "");
////                    tempInputNode.setDataset(tempInput);
////                    planNode.addInput(tempInputNode);
////
////                    WorkflowNode in = null; // materialized dataset for position i
////                    if( tempInput.checkMatch(in.dataset)){
////                        logger.info("match");
////
////                    }
////                    else{
////                        // move needed
////                        logger.info("move");
////                        // compute cost of move
////                    }
////                }
////
////
////
////
////
////
////
////
////                    planNode.inputs = initialNodes.get(nodeIndex).inputs;
////                planNode.outputs = initialNodes.get(nodeIndex).outputs;
////                planNode.setOperator(op);
////                logger.info(op.getEngine()+","+op.opName+" selected for "+initialNodes.get(nodeIndex).getName());
////
////                HashMap<Integer, Double> operatorExecution = new HashMap<>();
////
////
////                if (initialNodes.get(nodeIndex).parents.isEmpty()) {
////                    // compute metrics of one operator
////                    logger.info("Parent node");
////                    for (Map.Entry<String, Integer> obj : objectives.entrySet()) {
////                        double targetMetric = op.getMettric(obj.getKey(), initialNodes.get(nodeIndex).inputs);
////                        operatorExecution.put(obj.getValue(), targetMetric);
////                    }
////
////                }else{
////                    logger.info("child node");
////                    // check if outputs of parents can match with the inputs of this operator
////                    int inpCounter = 0;
////                    for(WorkflowNode inDataset : initialNodes.get(nodeIndex).inputs){
////                        logger.info("Input: "+inDataset.dataset.datasetName);
////
////                        // check for match
////                        boolean existsMatch = false;
////                        for(int parent : initialNodes.get(nodeIndex).parents){
////                            WorkflowNode parentOperator = plan.get(parent);
////                            logger.info("Parent in plan: "+parentOperator.getName());
////
////                            for(WorkflowNode datasetOutputs : parentOperator.outputs){
////                                logger.info("Check if "+inDataset.dataset.datasetName+
////                                        " matches with "+datasetOutputs.dataset.datasetName);
////
////                                if(inDataset.dataset.checkMatch(datasetOutputs.dataset)){
////                                    logger.info("matches");
////                                    existsMatch = true;
////                                    costMetrics = combineCosts(costMetrics, parentOperator.getOptimalMetrics());
////                                    break;
////                                }
////
////                            }
////                            if(existsMatch) break;
////                        }
////
////                        // compute time for child to run
////                        for (Map.Entry<String, Integer> obj : objectives.entrySet()) {
////                            double targetMetric = op.getMettric(obj.getKey(), initialNodes.get(nodeIndex).inputs);
////                            operatorExecution.put(obj.getValue(), targetMetric);
////                        }
////                        operatorExecution = addCosts(costMetrics, operatorExecution);
////
//////                        if(existsMatch){
//////                            logger.info("Matches with Parent: "+initialNodes.get(matchedParent).getName());
//////                        }else{
//////
//////                        }
//////                        //TODO:...........
////
////                    }
////                }
////
////                // add computed metrics to the plan
////
////                planNode.setOptimalMetrics(operatorExecution);
////                plan.put(nodeIndex, planNode);
////            }
//
//            for(Map.Entry<Integer, Double> objScoreEntry : costMetrics.entrySet()) {
//                solution.setObjective(objScoreEntry.getKey(), objScoreEntry.getValue());
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }









