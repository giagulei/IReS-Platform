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


package gr.ntua.cslab.asap.daemon;

import gr.ntua.cslab.asap.daemon.rest.YarnMetricsClient;
import gr.ntua.cslab.asap.operators.Dataset;
import gr.ntua.cslab.asap.operators.Operator;
import gr.ntua.cslab.asap.rest.beans.OperatorDictionary;
import gr.ntua.cslab.asap.rest.beans.WorkflowDictionary;
import gr.ntua.cslab.asap.staticLibraries.OperatorLibrary;
import gr.ntua.cslab.asap.utils.Utils;
import gr.ntua.cslab.asap.workflow.MObjMaterializedWorkflow;
import gr.ntua.cslab.asap.workflow.MultObjAbstractWorkflow;
import gr.ntua.cslab.asap.workflow.WorkflowNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.sourceforge.jeval.EvaluationException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.cli.YarnCLI;
import org.apache.log4j.Logger;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

import com.cloudera.kitten.client.YarnClientService;
import com.cloudera.kitten.client.params.lua.LuaYarnClientParameters;
import com.cloudera.kitten.client.service.YarnClientServiceImpl;
import com.cloudera.kitten.util.LocalDataHelper;

public class RunningWorkflowLibrary {
	private static ConcurrentHashMap<String,WorkflowDictionary> runningWorkflows;
	private static ConcurrentHashMap<String,WorkflowDictionary> toRunWorkflows;
	private static ConcurrentHashMap<String,MultObjAbstractWorkflow> runningAbstractWorkflows;
	private static ConcurrentHashMap<String,YarnClientService> runningServices;
	public static ConcurrentHashMap<String,ApplicationReport> workflowsReport;
	public static ConcurrentHashMap<String, String> nodes;
	public static ConcurrentHashMap< String, ConcurrentHashMap< String, ArrayList< String>>> runningContainers;
	public static YarnConfiguration yconf;
	
	private static Configuration conf;
	private static Logger logger = Logger.getLogger(RunningWorkflowLibrary.class.getName());

	public static void initialize() throws Exception{
		runningContainers = new ConcurrentHashMap< String, ConcurrentHashMap< String, ArrayList< String>>>();
		yconf = new YarnConfiguration();
		nodes = new ConcurrentHashMap< String, String>();
		runningWorkflows = new ConcurrentHashMap<String, WorkflowDictionary>();
		runningServices = new ConcurrentHashMap<String, YarnClientService>();
		workflowsReport =  new ConcurrentHashMap<String, ApplicationReport>();
		runningAbstractWorkflows=  new ConcurrentHashMap<>();
		toRunWorkflows = new ConcurrentHashMap<String,WorkflowDictionary>();
	    conf = new Configuration();
	    for( Entry<Object, Object> p : ServerStaticComponents.properties.entrySet()){
			conf.set(p.getKey().toString(), p.getValue().toString());
	    }
        (new Thread(new YarnServiceHandler())).start();
	}
	
	public static Map<String,YarnClientService> getRunningServices(){
		return runningServices;
	}
	public static void removeRunningService(String key) throws Exception {
		runningServices.remove(key);
		WorkflowDictionary w = RunningWorkflowLibrary.getWorkflow(key);
		
		logger.info("Removing: "+key);
	    //MaterializedWorkflow1 w = MaterializedWorkflowLibrary.get(e.getKey());
	    for(OperatorDictionary op : w.getOperators()){
			logger.info("Checking: "+op.getName());
			if(op.getIsOperator().equals("true") && op.getStatus().equals("completed")){
				Operator operator = OperatorLibrary.getOperator(op.getNameNoID());
				operator.reConfigureModel();
			}
	    }
	}
	
	public static WorkflowDictionary getWorkflow(String name) throws Exception{
			WorkflowDictionary wd = runningWorkflows.get(name);
			wd.replaceDescription("\n","<br>");
			/*Random r = new Random();
			for(OperatorDictionary op: wd.getOperators()){
				if(r.nextBoolean()){
					op.setStatus("running");
				}
				else{
					op.setStatus("stopped");
				}
			}*/
			//return mw.toWorkflowDictionary("<br>");
			return wd;
//		}
	}
	
	public static WorkflowDictionary getWorkflowToRun(String name) throws Exception{
		if(toRunWorkflows.containsKey(name)){
			WorkflowDictionary wd = toRunWorkflows.get(name);
			wd.replaceDescription("\n","<br>");
			return wd;
		}
		else{
			WorkflowDictionary wd = runningWorkflows.get(name);
			wd.replaceDescription("\n","<br>");
			return wd;
		}
	}	

	public static List<String> getWorkflows() {
		return new ArrayList<String>(runningWorkflows.keySet());
	}

	public static String executeWorkflow(MObjMaterializedWorkflow materializedWorkflow) throws Exception {
		runningAbstractWorkflows.put(materializedWorkflow.name, materializedWorkflow.getAbstractWorkflow());
		WorkflowDictionary wd = materializedWorkflow.toWorkflowDictionary("\n");
		for(OperatorDictionary op : wd.getOperators()){
			if(op.getStatus().equals("running"))
				op.setStatus("warn");
		}
		
		YarnClientService service = startYarnClientService(wd, materializedWorkflow);
		runningServices.put(materializedWorkflow.name, service);
		runningWorkflows.put(materializedWorkflow.name, wd);
		return materializedWorkflow.name;
	}

	private static YarnClientService startYarnClientService(WorkflowDictionary d, MObjMaterializedWorkflow mw) throws Exception {
	    YarnClientService service = null;
		HashMap<String,String> operators = new HashMap<String, String>();
		HashMap<String,String> inputDatasets = new HashMap<String, String>();
		LuaYarnClientParameters params = null;
		String luafilename = null;

		for(OperatorDictionary op : d.getOperators()){
			if(op.getIsOperator().equals("true")){
				/* vpapa: retrieve the .lua file specified for this operator from
					operator's description
				*/
				luafilename = op.getPropertyValue( "Execution.LuaScript");
				logger.info( "The .lua file is: " + luafilename);
				operators.put( op.getName(), OperatorLibrary.operatorDirectory + "/" + op.getNameNoID() + "/" + luafilename);
			}
			else{
				if(op.getInput().isEmpty()){
					Dataset inDataset = new Dataset(op.getName());
					inDataset.readPropertiesFromString(op.getDescription());
					logger.info("Adding dataset: "+op.getName()+" "+inDataset.getParameter("Execution.path"));
					inputDatasets.put(op.getName(), inDataset.getParameter("Execution.path"));
				}
			}
		}
		logger.info("Operators: "+operators);
		logger.info("InputDatasets: "+inputDatasets);
		String tmpFilename = mw.directory+"/" +UUID.randomUUID()+".xml";
		Utils.marshall(d, tmpFilename);
	    /* vpapa: catch a NullPointerException if a .lua is missing
		*/
		try{
			params = new LuaYarnClientParameters( mw.name, tmpFilename, operators, inputDatasets, conf, new HashMap<String, Object>(), new HashMap<String, String>());
		}
		catch( NullPointerException npe){
			logger.info( "ERROR: Check that the .lua file " + luafilename + " exists!");
			logger.info( "It is possible that it is the cause of this exception.");
		}
	    service = new YarnClientServiceImpl(params);

	    service.startAndWait();
	    if (!service.isRunning()) {
	    	logger.error("Service failed to startup, exiting...");
	    	throw new Exception("Service failed to startup, exiting...");
	    }
	    return service;
	}

	public static String getState(String id) {
		ApplicationReport report = workflowsReport.get(id);
		if(report==null)
			return "";
		else{
			if(report.getYarnApplicationState().equals(YarnApplicationState.FINISHED)){
				String ret = "FINISHED";
				ret+=" "+report.getFinalApplicationStatus();
				return ret;
			}
			else{
				return report.getYarnApplicationState().toString();
			}
		}
	}

	public static String getTrackingUrl(String id) {
		ApplicationReport report = workflowsReport.get(id);
		if(report==null)
			return "";
		else
			return report.getTrackingUrl();
	}

	/**
  	 * Returns an html string containing the http url of ApplicationMaster's container
  	 * logs
  	 * 
  	 * @author Vassilis Papaioannou
  	 * @parm id			workflow's id
  	 * @return logs  	the html string
  	 */
	public static String getApplicationLogs( String id) throws Exception {
		String trackingUrl = getTrackingUrl( id);
		String appname = trackingUrl.indexOf( "application_") > 0 ? trackingUrl.substring( trackingUrl.indexOf( "application_")) : "0";
		String logs = appname.equals( "0") ? "" : YarnMetricsClient.issueRequestApplicationLogs( new YarnConfiguration(), appname);
		return logs;
	}	
	
	/**
  	 * Returns an html string containing the http urls of workflow's containers
  	 * logs
  	 * 
  	 * @author Vassilis Papaioannou
  	 * @parm id			workflow's id
  	 * @return logs  	the html string
  	 */
	public static String getApplicationContainersLogs( String id) throws Exception {
		String trackingUrl = getTrackingUrl( id);
		String appname = trackingUrl.indexOf( "application_") > 0 ? trackingUrl.substring( trackingUrl.indexOf( "application_")) : "0";
		String logs = "";
		String log_dirs = yconf.get( "yarn.nodemanager.log-dirs").trim();
		log_dirs = log_dirs.startsWith( "$") ? "/logs/userlogs" : log_dirs;
		ConcurrentHashMap< String, ArrayList< String>> temp = null; 
		ArrayList< String> templ = null;
		if( ! appname.equals( "0")){
			if( runningContainers.get( appname) == null){
				runningContainers.put( appname, new ConcurrentHashMap< String, ArrayList< String>>());
			}
			temp = YarnMetricsClient.issueRequestApplicationContainersLogs( yconf, nodes, appname);
			for( Entry<String, String> node : nodes.entrySet()){
				if( temp.get( node.getKey()) != null){
					//System.out.println( "R NODE " + node.getKey() + " at port " + node.getValue());
					if( runningContainers.get( appname).get( node.getKey()) == null){
						runningContainers.get( appname).put( node.getKey(), new ArrayList< String>());
					}
					runningContainers.get( appname).get( node.getKey()).addAll( temp.get( node.getKey()));
					templ = new ArrayList< String>( new HashSet< String>( runningContainers.get( appname).get( node.getKey())));
					runningContainers.get( appname).put( node.getKey(), templ);
				}
				if( runningContainers.get( appname).get( node.getKey()) != null){
					for( String contlog : runningContainers.get( appname).get( node.getKey())){
						if( contlog != null && ! contlog.endsWith( "000001")){
							contlog = "http://" + node.getKey() + ":" + node.getValue() + log_dirs + "/" + appname + contlog;
							logs += "<li><a href=\"" + contlog + "\">" + contlog + "</a></li>";
						}
					}
				}				
				//System.out.println( "LOGS: " + logs);
			}
		}
		return logs;
	}

	public static void setWorkFlow(String id, WorkflowDictionary workflow) {
		runningWorkflows.put(id, workflow);
	}

	public static void replan(String id) throws Exception {
		HashMap<String,WorkflowNode> materializedDatasets = new HashMap<String,WorkflowNode>();
		WorkflowNode dataset_node = null;
		Dataset temp = null;
		MultObjAbstractWorkflow aw = null;
		WorkflowDictionary replanned_workflow = null;
		List< String> sorted_nodes = new ArrayList< String>();
		
		WorkflowDictionary wd = runningWorkflows.get(id);
		MObjMaterializedWorkflow materialiazedWorkflow = new MObjMaterializedWorkflow(id, "/tmp");
		materialiazedWorkflow.readFromWorkflowDictionary(wd);
		for(OperatorDictionary op : wd.getOperators()){
			if(op.getIsOperator().equals("false") && op.getIsAbstract().equals("false") && op.getStatus().equals("completed")){
				logger.info( "Operator: "+ op.getName()+ "\tAbstract name: " + op.getAbstractName());				
				sorted_nodes.add( op.getAbstractName());
				dataset_node = new WorkflowNode(false, false,op.getAbstractName());
				temp = new Dataset(op.getName());
				temp.readPropertiesFromString(op.getDescription().replace("<br>", "\n"));
				dataset_node.setDataset(temp);
				materializedDatasets.put(op.getAbstractName(), dataset_node);
			}
		}
		for( Entry< String, WorkflowNode> node : materializedDatasets.entrySet()){
			logger.info( "WorkflowNode: " + node.getKey() + "\t" + node.getValue());
		}
		logger.info("Datasets: "+materializedDatasets);
		aw = runningAbstractWorkflows.get(id);
		replanned_workflow = aw.replan(materializedDatasets, 100).toWorkflowDictionary( "\n");

		/*vpapa: the returned and replanned workflow may be empty for the failed operator
		 * and the current set of completed data sets. For this, we should go one executed
		 * operator back taking in account the related data sets and then ask for a new plan 
		 */
		/*
		logger.info( "#################");
		logger.info( "#################");
		logger.info( "#################");
		*/
		if( replanned_workflow.getOperators() == null || replanned_workflow.getOperators().isEmpty()){
			logger.info( "Empty or null replanned workflow: " + replanned_workflow.getOperators());
			replanned_workflow = trimReplannedWorkflow( materializedDatasets, aw, sorted_nodes);
		}
		/*
		else{
			for( OperatorDictionary node : replanned_workflow.getOperators()){
				logger.info( "WorkflowNode: " + node.getName() + "\t" + node.getStatus());
			}
		}
		*/
		toRunWorkflows.put(id, replanned_workflow);
	}
	
	/** 
	 * Returns an alternative execution plan( WorkflowDictionary) if it exists. Otherwise it returns
	 * a new, empty WorkflowDictionary.
	 * 
	 * @author Vassilis Papaioannou
	 * @param materializeddatasets	the set of inputs already have been run
	 * @param aw					the abstract workflow for which to find an alternative plan
	 * @param sn					the reduced set of materialized inputs for which to search for an alternative plan
	 * @return WorkflowDictionary	the alternative execution path
	 */
	private static WorkflowDictionary trimReplannedWorkflow( HashMap< String, WorkflowNode> materializedDatasets, MultObjAbstractWorkflow aw, List< String> sn) throws Exception{
		List< String> failed_operators = new ArrayList< String>();

		/*
		logger.info( "#################");
		logger.info( "#################");
		logger.info( "#################");
		logger.info( "TRIM Datasets: " + materializedDatasets);
		logger.info( "TRIM KEY Datasets: " + sn);
		*/
		//only the very first dataset of the workflow has remained, so there is no alternative workflow
		if( sn.size() == 1){
			return new WorkflowDictionary();
		}
		
		WorkflowDictionary replanned_workflow = null;

		//it is assumed that the nodes in 'sn' reflect the physical order of workflow operators
		//remove the next two data sets that is assumed that correspond to the last completed operator input and output
		//and update the 'sn' also
		int i = sn.size() - 1;
		materializedDatasets.remove( sn.get( i));
		//logger.info( "Datasets: " + materializedDatasets);
		materializedDatasets.remove( sn.get( i - 1));
		//logger.info( "Datasets: " + materializedDatasets);
		failed_operators.add( sn.get( sn.size() - 1));
		sn.remove( sn.size() - 1);
		//logger.info( "KEY Datasets: " + sn);
		failed_operators.add( sn.get( sn.size() - 1));
		sn.remove( sn.size() - 1);
		//logger.info( "KEY Datasets: " + sn);
		
		replanned_workflow = aw.replan( materializedDatasets, 100).toWorkflowDictionary( "\n");
		if( replanned_workflow.getOperators().isEmpty()){
			logger.info( "Empty or null replanned workflow: " + replanned_workflow.getOperators());
			replanned_workflow = trimReplannedWorkflow( materializedDatasets, aw, sn);
		}
		//logger.info( "FAILED OPERATORS: " + failed_operators);
		if( replanned_workflow.failedops == null){
			replanned_workflow.failedops = new ArrayList< String>();
		}
		replanned_workflow.failedops.addAll( failed_operators);
		return replanned_workflow;
	}
}
