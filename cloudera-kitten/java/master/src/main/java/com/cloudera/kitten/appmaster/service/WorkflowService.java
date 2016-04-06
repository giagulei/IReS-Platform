/**
 * Copyright (c) 2012, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */
package com.cloudera.kitten.appmaster.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.cloudera.kitten.ContainerLaunchContextFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterResponse;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerExitStatus;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.client.api.AMRMClient;
import org.apache.hadoop.yarn.client.api.AMRMClient.ContainerRequest;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.hadoop.yarn.client.api.async.NMClientAsync;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

import com.cloudera.kitten.ContainerLaunchParameters;
import com.cloudera.kitten.appmaster.AbstractClient;
import com.cloudera.kitten.appmaster.ApplicationMasterParameters;
import com.cloudera.kitten.appmaster.ApplicationMasterService;
import com.cloudera.kitten.appmaster.params.lua.WorkflowParameters;
import com.cloudera.kitten.lua.LuaFields;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AbstractScheduledService;

import gr.ntua.cslab.asap.rest.beans.OperatorDictionary;
import gr.ntua.cslab.asap.rest.beans.WorkflowDictionary;

public class WorkflowService extends
    AbstractScheduledService implements ApplicationMasterService,
    AMRMClientAsync.CallbackHandler {

  private static final Log LOG = LogFactory.getLog(WorkflowService.class);

  public final WorkflowParameters parameters;
  public final YarnConfiguration conf;
  private final AtomicInteger totalFailures = new AtomicInteger();
  private HashMap<String,ContainerTracker> trackers;
  private HashMap<ContainerId, ContainerTracker> containerAllocation;
  public int prior;
  public AMRMClientAsync<ContainerRequest> resourceManager;
  private boolean hasRunningContainers = false;
  private Throwable throwable;
  public HashMap< String, String> services_n_status = null;
  public HashMap< String, String> replanned_operators = null;
  private HashMap<String,ContainerTracker> replanned_trackers = null;
  
protected ContainerLaunchContextFactory factory;

  public WorkflowService(WorkflowParameters parameters, Configuration conf) {
	  this.trackers = new HashMap<String, ContainerTracker>();
    this.parameters = Preconditions.checkNotNull(parameters);
    this.conf = new YarnConfiguration(conf);
    this.prior=1;
  }

  @Override
  public ApplicationMasterParameters getParameters() {
    return parameters;
  }

  @Override
  public boolean hasRunningContainers() {
    return hasRunningContainers;
  }

  @Override
  protected void startUp() throws IOException {
	  this.containerAllocation = new HashMap<ContainerId, ContainerTracker>();
    this.resourceManager = AMRMClientAsync.createAMRMClientAsync(1000, this);
    this.resourceManager.init(conf);
    this.resourceManager.start();

    RegisterApplicationMasterResponse registration;
    try {
      registration = resourceManager.registerApplicationMaster(
          parameters.getHostname(),
          parameters.getClientPort(),
          parameters.getTrackingUrl());
    } catch (Exception e) {
      LOG.error("Exception thrown registering application master", e);
      stop();
      return;
    }

    factory = new ContainerLaunchContextFactory(
        registration.getMaximumResourceCapability());

    trackers = parameters.createTrackers(this);

    for(ContainerTracker t : trackers.values()){
	    t.init(factory);
    }

    /*for ( Entry<String, ContainerLaunchParameters> e : parameters.getContainerLaunchParameters().entrySet()) {
    	ContainerTracker tracker = new ContainerTracker(e.getValue());
    	LOG.info("Operator: " + e.getKey());
    	trackers.put(e.getKey(),tracker);
    }
    LOG.info("Trackers: " + trackers);

    trackers.get("Move_MySQL_HBase").addNextTracker(trackers.get("HBase_HashJoin"));
    trackers.get("HBase_HashJoin").addNextTracker(trackers.get("Sort2"));

    trackers.get("Move_MySQL_HBase").init(factory);*/

    this.hasRunningContainers = true;
  }

  @Override
  protected void shutDown() {
    // Stop the containers in the case that we're finishing because of a timeout.
    LOG.info("Stopping trackers");
    this.hasRunningContainers = false;

    for (ContainerTracker tracker : trackers.values()) {
      if (tracker.hasRunningContainers()) {
        tracker.kill();
      }
    }
    FinalApplicationStatus status;
    String message = null;
    if (state() == State.FAILED || totalFailures.get() > parameters.getAllowedFailures()) {
      //TODO: diagnostics
      status = FinalApplicationStatus.FAILED;
      if (throwable != null) {
        message = throwable.getLocalizedMessage();
      }
    } else {
      status = FinalApplicationStatus.SUCCEEDED;
    }
    LOG.info("Sending finish request with status = " + status);
    try {
      resourceManager.unregisterApplicationMaster(status, message, null);
    } catch (Exception e) {
      LOG.error("Error finishing application master", e);
    }
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedRateSchedule(0, 1, TimeUnit.SECONDS);
  }

  @Override
  protected void runOneIteration() throws Exception {

	AbstractClient.issueRequest(conf, parameters.jobName, parameters.workflow);
    String[] response = AbstractClient.issueRequestClusterStatus( conf).split( "\n");
    services_n_status = new HashMap<String, String>();
    for( String service: response ){
        services_n_status.put( service.split( ":")[ 0].trim(), service.split( ":")[ 1].trim());
    }
    
    for( String s : services_n_status.keySet())
        System.out.println( "Service " + s + " has status " + services_n_status.get( s));
    
    //read workflow operators' state until the running operators are found and if are found
    //check that operators' needed services are up. If these services are not up replan
    //workflow execution
    String service = null;
    WorkflowDictionary replanned_workflow = null;
    boolean replan = false;
    HashMap<String, ContainerTracker> newTrackers = null;
    
    for( OperatorDictionary opd : parameters.workflow.getOperators()){
        //we are looking for operators and we are looking for two specific properties
        //of them, Constraints.Inputx.Engine and Constraints.Outputy.Engine for each
        //input x and output y
        if( opd.getStatus().toLowerCase().equals( "running") && opd.getIsOperator().toLowerCase().equals( "true")){
            service = opd.getEngine();
            //System.out.println( "\n\nService is " + service);

            if( service != null){
                //System.out.println( "Services and their status " + services_n_status.get( service ));
                if( services_n_status.get( service).toString().equals( "true")){
                    LOG.info( "Service " + service + " is up for operator " + opd.getName());
                }
                else{
                    LOG.info( "Service " + service + " is not up for operator " + opd.getName());
                    LOG.info( "Workflow " + parameters.jobName + " should be replanned.");
                    //replan for this operator if it hasn't replanned earlier
                    if( replanned_operators == null){
                    	replanned_operators = new HashMap< String, String>();
                    	replan = true;
                    }
                    else{
	                    if( replanned_operators.isEmpty() || replanned_operators.get( opd.getName()) == null){
	                    	replan = true;
	                    }
                    }
                    if( replan){
                    	//this operator has been failed so mark it
                    	opd.setStatus( "failed");
                    	//then make the re - plan
                    	WorkflowDictionary before_workflow_replanning = parameters.workflow;
                    	WorkflowDictionary after_workflow_replanning = null;
                    	LOG.info( "CURRENT TRACKERS BEFORE REPLANNING: " + trackers);
                        AbstractClient.issueRequestReplan( conf, parameters.jobName);
                        after_workflow_replanning = AbstractClient.issueRequestRunningWorkflow( conf, parameters.jobName);
                        replanned_workflow = AbstractClient.issueRequestToRunWorkflow( conf, parameters.jobName);
                        replanned_operators.put( opd.getName(), "true");
                        LOG.info( "Replanned operators are\n" + replanned_operators + "\n");
                        LOG.info( "WORKFLOW TO REPLAN is\n");
                        for( OperatorDictionary opdd : before_workflow_replanning.getOperators()){
                        	LOG.info( "Operator: " + opdd.getName() + "\twith status " + opdd.getStatus() + "\n");
                        }
                        LOG.info( "AFTER WORKFLOW REPLAN\n");
                        for( OperatorDictionary opdd : after_workflow_replanning.getOperators()){
                        	LOG.info( "Operator: " + opdd.getName() + "\twith status " + opdd.getStatus() + "\n");
                        }
                        LOG.info( "REPLANNED WORKFLOW\n");
                        for( OperatorDictionary opdd : replanned_workflow.getOperators()){
                        	LOG.info( "Operator: " + opdd.getName() + "\twith status " + opdd.getStatus() + "\n");
                        }
                        LOG.info( "CURRENT TRACKERS AFTER REPLANNING: " + trackers);
                        //all replanned operators are marked as "failed"
                        //all operators returned from replan and they are have been "completed" should remain at this state
                        //
                    	for( OperatorDictionary oprw : replanned_workflow.getOperators()){
                    		for( OperatorDictionary opawr : after_workflow_replanning.getOperators()){
                    			//find the common operators between the two WorkflowDictioanary
                        		if( oprw.getName().equals( opawr.getName())){
                        			//we do not want to re execute operators already run and included into the
                        			//replanned workflow
                        			if( !opawr.getStatus().equals( "completed")){
                        				opawr.setStatus( oprw.getStatus());	
                        			}
                        			
                        		}
                        	}
                        	//if( replanned_operators.get( opawr.getName()) != null){
                        	//	opawr.setStatus( "failed");
                           	//}
                        }
                        newTrackers = parameters.createTrackers( this);
                        LOG.info( "NEW TRACKERS AFTER REPLANNING: " + newTrackers);
                    }
                }
            }
        }
    }

    if (totalFailures.get() > parameters.getAllowedFailures() ||
        allTrackersFinished()) {
      stop();
    }
  }

  private boolean allTrackersFinished() {
	  boolean ret = true;
	  for(ContainerTracker t : trackers.values()){
		  if(t.hasMoreContainers()){
			 ret =false;
			 break;
		  }
	  }
	  //LOG.info("allTrackersFinished: "+ret);
	  return ret;
  }

// AMRMClientHandler methods
  @Override
  public void onContainersCompleted(List<ContainerStatus> containerStatuses) {
    LOG.info(containerStatuses.size() + " containers have completed");
    for (ContainerStatus status : containerStatuses) {
      int exitStatus = status.getExitStatus();
      if (0 != exitStatus) {
        // container failed
        if (ContainerExitStatus.ABORTED != exitStatus) {
            totalFailures.incrementAndGet();
      	  containerAllocation.remove(status.getContainerId()).containerCompleted(status.getContainerId());
        } else {
          // container was killed by framework, possibly preempted
          // we should re-try as the container was lost for some reason
        }
      } else {
        // nothing to do
        // container completed successfully
          LOG.info("Container id = " + status.getContainerId() + " completed successfully");
    	  containerAllocation.remove(status.getContainerId()).containerCompleted(status.getContainerId());
      }
    }
  }

  @Override
  public void onContainersAllocated(List<Container> allocatedContainers) {
    LOG.info("Allocating " + allocatedContainers.size() + " container(s).");
    Set<Container> assigned = Sets.newHashSet();
    for (ContainerTracker tracker : trackers.values()) {
        for (Container allocated : allocatedContainers) {
            if (tracker.isInitilized && tracker.needsContainers()) {
	          if (!assigned.contains(allocated) && tracker.matches(allocated)) {
	        	  LOG.info("Allocated vcores: "+allocated.getResource().getVirtualCores());
	              tracker.launchContainer(allocated);
	              assigned.add(allocated);
	              containerAllocation.put(allocated.getId(), tracker);
	          }
            }
        }
    }
    for(Entry<ContainerId, ContainerTracker> e: containerAllocation.entrySet()){
    	LOG.info("Allocated: "+e.getKey()+" to operator: "+e.getValue().params.getName());
    }
    /*if (assigned.size() < allocatedContainers.size()) {
      LOG.error(String.format("Not all containers were allocated (%d out of %d)", assigned.size(),
          allocatedContainers.size()));
      stop();
    }*/
  }

  @Override
  public void onShutdownRequest() {
    stop();
  }

  @Override
  public void onNodesUpdated(List<NodeReport> nodeReports) {
    //TODO
  }

  @Override
  public float getProgress() {
    int num = 0, den = 0;
    for (ContainerTracker tracker : trackers.values()) {
      num += tracker.completed.get();
      den += tracker.params.getNumInstances();
    }
    if (den == 0) {
      return 0.0f;
    }
    return ((float) num) / den;
  }

  @Override
  public void onError(Throwable throwable) {
    this.throwable = throwable;
    stop();
  }
}
