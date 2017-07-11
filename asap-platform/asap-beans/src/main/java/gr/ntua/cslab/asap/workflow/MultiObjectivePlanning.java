package gr.ntua.cslab.asap.workflow;

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

    private HashMap<Integer, Operator> materializedOperators;
    private WorkflowNode target; //TODO: make it List<WorkflowNode> targets

    private static Logger logger = Logger.getLogger(MultiObjectivePlanning.class.getName());

    public MultiObjectivePlanning(){
        super(initialNodes.size(), objectives.size());
        try {
            findMaterializedOperators();
        } catch (Exception e) {
            logger.error("Error when tries to find materialized operators");
            e.printStackTrace();
        }
    }

    private void findMaterializedOperators() throws Exception {
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


    @Override
    public void evaluate(Solution solution) {
        try {
            int[] mapping = EncodingUtils.getInt(solution);

            List<WorkflowNode> plan = new ArrayList<>();
            HashMap<Integer, Double> costMetrics = initializeCostMetrics();

            // afou ta sarwnw me ascending keys, prwta 8a dw goneis kai meta paidia
            for(Integer nodeIndex : initialNodes.navigableKeySet()) {

                Operator op = materializedOperators.get(mapping[nodeIndex]);

                if (initialNodes.get(nodeIndex).parents.isEmpty()) {
                    HashMap<Integer, Double> operatorExecution = new HashMap<>();
                    for (Map.Entry<String, Integer> obj : objectives.entrySet()) {
                        double targetMetric = op.getMettric(obj.getKey(), initialNodes.get(nodeIndex).inputs);
                        operatorExecution.put(obj.getValue(), targetMetric);
                    }
                }else{
                    // check if outputs of parents can match with the inputs of this operator
                    for(WorkflowNode inDataset : initialNodes.get(nodeIndex).inputs){
                        // check if there is at least one parent output that can match with this
                        boolean existsMatch = false;
                        for(int parent : initialNodes.get(nodeIndex).parents){
                            WorkflowNode parentOperator = initialNodes.get(parent);
                            for(WorkflowNode datasetOutputs : parentOperator.outputs){
                                if(inDataset.dataset.checkMatch(datasetOutputs.dataset)){
                                    existsMatch = true;
                                    break;
                                }
                            }
                            if(existsMatch) break;
                        }

                        //TODO:...........

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //================================
//        for(Map.Entry<Integer, Double> objScoreEntry : costMetrics.entrySet()) {
//            solution.setObjective(objScoreEntry.getKey(), objScoreEntry.getValue());
//        }
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
