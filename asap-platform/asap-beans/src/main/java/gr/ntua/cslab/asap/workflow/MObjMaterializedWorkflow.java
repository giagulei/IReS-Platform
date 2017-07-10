package gr.ntua.cslab.asap.workflow;

import java.util.HashMap;

/**
 * Created by giagulei on 10/7/2017.
 */
public class MObjMaterializedWorkflow extends MaterializedWorkflow1 {

    private MultObjAbstractWorkflow abstractWorkflow;

    public HashMap<String, String> optimizationMetrics;


    public MObjMaterializedWorkflow(String name, String directory) {
        super(name, directory);
    }

    public void setPolicy(HashMap<String, String> groupInputs, HashMap<String, String> optimizationFunctions) {
        this.groupInputs = groupInputs;
        this.optimizationMetrics = optimizationFunctions;
    }

    public MultObjAbstractWorkflow getAbstractWorkflow() {
        return abstractWorkflow;
    }

}
