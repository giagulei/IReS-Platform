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


package gr.ntua.cslab.asap.daemon.rest;


import gr.ntua.cslab.asap.daemon.ServerStaticComponents;
import gr.ntua.cslab.asap.operators.Operator;
import gr.ntua.cslab.asap.rest.beans.OperatorDescription;
import gr.ntua.cslab.asap.staticLibraries.OperatorLibrary;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.xml.ws.WebServiceException;

import org.apache.log4j.Logger;

@Path("/operators/")
public class Operators {

    public Logger logger = Logger.getLogger(Operators.class);
    
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String listOperators() {
    	List<String> l = OperatorLibrary.getOperators();
    	String ret = "<ul>";
    	for(String op : l){
			ret+= "<li><a href=\"operators/"+op+"\">"+op+"</a></li>";
    		
    	}
    	ret+="</ul>";
        return ret;
    }

    @GET
    @Path("{id}/")
    @Produces(MediaType.TEXT_HTML)
    public String getApplicationInfo(@PathParam("id") String id) {
    	return OperatorLibrary.getOperatorDescription(id);
    }
    
    @GET
    @Path("json/{id}/")
	@Produces("application/json")
    public OperatorDescription getOperatorInfoJson(@PathParam("id") String id) {
    	return OperatorLibrary.getOperatorDescriptionJSON(id);
    }
    
    
    @GET
    @Path("XML/{id}/")
	@Produces("application/XML")
    public OperatorDescription getOperatorInfoXML(@PathParam("id") String id) {
    	return OperatorLibrary.getOperatorDescriptionJSON(id);
    }
    

    @GET
    @Path("add/")
	@Produces("application/XML")
    public String addOperator(
            @QueryParam("opname") String opname,
            @QueryParam("opString") String opString) throws Exception {
    	
    	OperatorLibrary.addOperator(opname, opString);
    	return "OK";
    }
    
    @GET
    @Path("delete/")
	@Produces("application/XML")
    public String deleteOperator(
            @QueryParam("opname") String opname) throws Exception {
    	
    	OperatorLibrary.deleteOperator(opname);
    	return "OK";
    }
    
}
