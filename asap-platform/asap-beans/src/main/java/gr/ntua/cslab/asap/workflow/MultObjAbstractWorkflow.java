package gr.ntua.cslab.asap.workflow;

import gr.ntua.cslab.asap.staticLibraries.MaterializedWorkflowLibrary;
import gr.ntua.cslab.asap.staticLibraries.OperatorLibrary;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

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
    }

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

//        if(materilizedDatasets!=null)
//            materializedWorkflow.materilizedDatasets=materilizedDatasets;
//        else
//            materializedWorkflow.materilizedDatasets=new HashMap<>();

        materializedWorkflow.setAbstractWorkflow(this);
        materializedWorkflow.setPolicy(groupInputs, optimizationFunctions);

        WorkflowNode temp = null;
        Double bestCost = 0.0;
        Double tempCost = 0.0;
        List<WorkflowNode> bestPlan=null;

        for(WorkflowNode t : targets){

            logger.info("node: "+t.getName());

            logger.info("Inputs: ");
            for(WorkflowNode in : t.inputs){
                logger.info(in.getName());
            }

            logger.info("Outputs: ");
            for(WorkflowNode out : t.inputs){
                logger.info(out.getName());
            }


//            logger.info( "Materializing workflow node: " + t.toStringNorecursive());
//            List<WorkflowNode> l = t.materialize(materializedWorkflow,dpTable,t.getName());
//			/* vpapa: assert that WorkflowNode.materialize() returned something
//				valid
//			*/
//            if( l != null && !l.isEmpty()){
//                if(functionTarget.contains("min")){
//                    bestCost=Double.MAX_VALUE;
//                    for(WorkflowNode r : l){
//                        tempCost = dpTable.getCost(r.dataset);
//                        if(tempCost<bestCost){
//                            bestCost=tempCost;
//                            bestPlan=dpTable.getPlan(r.dataset);
//                        }
//                    }
//                }
//                else if(functionTarget.contains("max")){
//                    bestCost = -Double.MAX_VALUE;
//                    for(WorkflowNode r : l){
//                        tempCost = dpTable.getCost(r.dataset);
//                        if(tempCost>bestCost){
//                            bestCost=tempCost;
//                            bestPlan=dpTable.getPlan(r.dataset);
//                        }
//                    }
//                }
//				/* vpapa: target may have or may have not a dataset
//				*/
//                if( t.dataset != null){
//					/* vpapa: temp below is going to be the next WorkflowNode
//						having as materialized dataset the dataset of the current
//						target WorkflowNode and as input the current target itself
//					*/
//                    temp = new WorkflowNode(false, false, t.getName());
//                    temp.setDataset( t.dataset);
//                    //System.out.println(l);
//                    temp.addInputs(l);
//                    materializedWorkflow.addTarget(temp);
//                }
//                else{
//					/* vpapa: in the absence of a dataset still the operator should
//						be added to the workflow
//					*/
//                    temp = l.get( 0).inputs.get( 0);
//                    temp.setDataset( l.get( 0).dataset);
//                    materializedWorkflow.addTarget(temp);
//                }
//                bestPlan.add(t);
//                materializedWorkflow.setBestPlan(t.toStringNorecursive(), bestPlan);
//                logger.info("Optimal cost: "+bestCost);
//                materializedWorkflow.optimalCost=bestCost;
//            }
        }

        return materializedWorkflow;
    }// end of AbstractWorkflow1 materialize



}
