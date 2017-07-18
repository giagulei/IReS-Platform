package gr.ntua.cslab.asap.workflow;

import gr.ntua.cslab.asap.operators.AbstractOperator;
import gr.ntua.cslab.asap.operators.Dataset;
import gr.ntua.cslab.asap.operators.Operator;
import gr.ntua.cslab.asap.staticLibraries.ClusterStatusLibrary;
import gr.ntua.cslab.asap.staticLibraries.OperatorLibrary;
import org.apache.log4j.Logger;
import org.moeaframework.core.Solution;
import org.moeaframework.problem.AbstractProblem;
import org.moeaframework.core.variable.EncodingUtils;

import java.util.*;

/**
 * Created by giagulei on 11/7/2017.
 */
public class MultiObjectivePlanning extends AbstractProblem {

    public static TreeMap<Integer, WorkflowNode> initialNodes;
    public static HashMap<String, Integer> objectives;
    public static HashMap<String, String> optimizationFunctions;
    public static HashMap<Integer, Operator> materializedOperators;

    private WorkflowNode target; //TODO: make it List<WorkflowNode> targets

    private static Logger logger = Logger.getLogger(MultiObjectivePlanning.class.getName());

    public MultiObjectivePlanning(){
        super(initialNodes.size(), objectives.size());
    }

    public static void findMaterializedOperators() throws Exception {
        materializedOperators = new HashMap<>();
        int mOperatorsIndex = 0;
        for(WorkflowNode abstrOp : initialNodes.values()){
            List<Operator> operators = OperatorLibrary.getMatches(abstrOp.abstractOperator);
            logger.info( "For abstract operator " + abstrOp.abstractOperator.opName + " the available"
                    + " operator implementations are:\n " + operators);

            for(Operator op : operators) {
                if (!ClusterStatusLibrary.checkEngineStatus(op)) {
                    logger.info("Specified engine for operator " + abstrOp.abstractOperator.opName
                            + " is " + op.getEngine());
                    logger.info("and it is not running. For this, this operator will not be materialized");
                    logger.info("and consequently the corresponding workflow will not be materialized");
                    logger.info("if alternatives do not exist for the relative abstract operator.");
                    continue;
                } else {
                    materializedOperators.put(mOperatorsIndex, op);
                    mOperatorsIndex++;
                }
            }
        }
    }

    private HashMap<Integer, Double> initializeCostMetrics(){
        HashMap<Integer, Double> costMetrics = new HashMap<>();
        for(Integer objID : objectives.values()){
            costMetrics.put(objID, 0.0);
        }
        return costMetrics;
    }

    public boolean checkSelectionValidity(Operator generatedOperator, AbstractOperator abstractOperator){
        return generatedOperator.opName.contains(abstractOperator.opName);
    }

    public HashMap<Integer, Double> combineCosts(HashMap<Integer, Double> score1, HashMap<Integer, Double> score2){
        for(Map.Entry<String, Integer> obj : objectives.entrySet()){
            double s1 = score1.get(obj.getValue());
            double s2 = score2.get(obj.getValue());
            if(optimizationFunctions.get(obj.getKey()).equals("min")){
                score1.put(obj.getValue(), Math.max(s1, s2));
            }else if(optimizationFunctions.get(obj.getKey()).equals("max")){
                score1.put(obj.getValue(), Math.min(s1, s2));
            }
        }
        return score1;
    }

    public HashMap<Integer, Double> addCosts(HashMap<Integer, Double> score1, HashMap<Integer, Double> score2){
        for(Map.Entry<Integer, Double> metricEntry : score1.entrySet()){
            double score2OldValue = score2.get(metricEntry.getKey());
            score2.put(metricEntry.getKey(), metricEntry.getValue() + score2OldValue);
        }
        return score2;
    }


    @Override
    public void evaluate(Solution solution) {
        int datasetCount = 0;
        logger.info("MOEA evaluation");
        try {
            int[] mapping = EncodingUtils.getInt(solution);

            HashMap<Integer, WorkflowNode> plan = new HashMap<>();
            HashMap<Integer, Double> costMetrics = initializeCostMetrics();

            // afou ta sarwnw me ascending keys, prwta 8a dw goneis kai meta paidia
            for(Integer nodeIndex : initialNodes.navigableKeySet()) {

                Operator op = materializedOperators.get(mapping[nodeIndex]);
                if(!checkSelectionValidity(op, initialNodes.get(nodeIndex).abstractOperator)){
                    for(Map.Entry<Integer, Double> objScoreEntry : costMetrics.entrySet()) {
                        solution.setObjective(objScoreEntry.getKey(), Integer.MAX_VALUE);
                    }
                    return;
                }
                WorkflowNode planNode = new WorkflowNode(true, false,
                        initialNodes.get(nodeIndex).getAbstractName());
                planNode.inputs = initialNodes.get(nodeIndex).inputs;
                planNode.outputs = initialNodes.get(nodeIndex).outputs;
                planNode.setOperator(op);
                logger.info(op.getEngine()+","+op.opName+" selected for "+initialNodes.get(nodeIndex).getName());

                HashMap<Integer, Double> operatorExecution = new HashMap<>();


                if (initialNodes.get(nodeIndex).parents.isEmpty()) {
                    // compute metrics of one operator
                    logger.info("Parent node");
                    for (Map.Entry<String, Integer> obj : objectives.entrySet()) {
                        double targetMetric = op.getMettric(obj.getKey(), initialNodes.get(nodeIndex).inputs);
                        operatorExecution.put(obj.getValue(), targetMetric);
                    }

                }else{
                    logger.info("child node");
                    // check if outputs of parents can match with the inputs of this operator
                    int inpCounter = 0;
                    for(WorkflowNode inDataset : initialNodes.get(nodeIndex).inputs){
                        logger.info("Input: "+inDataset.dataset.datasetName);

                        // check for match
                        boolean existsMatch = false;
                        for(int parent : initialNodes.get(nodeIndex).parents){
                            WorkflowNode parentOperator = plan.get(parent);
                            logger.info("Parent in plan: "+parentOperator.getName());

                            for(WorkflowNode datasetOutputs : parentOperator.outputs){
                                logger.info("Check if "+inDataset.dataset.datasetName+
                                        " matches with "+datasetOutputs.dataset.datasetName);

                                if(inDataset.dataset.checkMatch(datasetOutputs.dataset)){
                                    logger.info("matches");
                                    existsMatch = true;
                                    costMetrics = combineCosts(costMetrics, parentOperator.getOptimalMetrics());
                                    break;
                                }

                            }
                            if(existsMatch) break;
                        }

                        // compute time for child to run
                        for (Map.Entry<String, Integer> obj : objectives.entrySet()) {
                            double targetMetric = op.getMettric(obj.getKey(), initialNodes.get(nodeIndex).inputs);
                            operatorExecution.put(obj.getValue(), targetMetric);
                        }
                        operatorExecution = addCosts(costMetrics, operatorExecution);

//                        if(existsMatch){
//                            logger.info("Matches with Parent: "+initialNodes.get(matchedParent).getName());
//                        }else{
//
//                        }
//                        //TODO:...........

                    }
                }

                // add computed metrics to the plan

                planNode.setOptimalMetrics(operatorExecution);
                plan.put(nodeIndex, planNode);
            }

            for(Map.Entry<Integer, Double> objScoreEntry : costMetrics.entrySet()) {
                solution.setObjective(objScoreEntry.getKey(), objScoreEntry.getValue());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    @Override
    public Solution newSolution() {
        Solution solution = new Solution(initialNodes.size(), objectives.size());
        for (Integer abstractOpIndx : initialNodes.keySet()) {
           solution.setVariable(abstractOpIndx, EncodingUtils.newInt(0,
                   materializedOperators.size()-1));

        }
        return solution;
    }



}
