package com.cloudera.kitten.appmaster;

import gr.ntua.cslab.asap.rest.beans.WorkflowDictionary;
import gr.ntua.cslab.asap.rest.beans.OperatorDictionary;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import javax.xml.transform.stream.StreamSource;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

public class AbstractClient {

	private static final Log LOG = LogFactory.getLog(AbstractClient.class);
    /**
     * Issues a new Request and returns a string with the response - if  any.
     * @param conf
     * @param requestType
     * @param document
     * @param input
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    public static String issueRequest(YarnConfiguration conf, String id, WorkflowDictionary workflow) {
    	String masterDNS = conf.get("yarn.resourcemanager.address").split(":")[0];
        String urlString = "http://"+masterDNS+":1323/runningWorkflows/report/"+id+"/";
        String ret="";
		try {
	        LOG.info("Issuing urlString: "+urlString);
			System.out.println("Issuing urlString: "+urlString);
	        URL url = new URL(urlString);
	        HttpURLConnection con = (HttpURLConnection) url.openConnection();

	        con.setRequestMethod("POST");

	        con.setRequestProperty("accept", "application/octet-stream");
	        con.setRequestProperty("Content-type", "application/octet-stream");
	        con.setDoInput(true);
	        con.setDoOutput(true);

	        OutputStream out = con.getOutputStream();
	        JAXBContext jaxbContext = JAXBContext.newInstance(WorkflowDictionary.class);
			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

			// output pretty printed
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

			jaxbMarshaller.marshal(workflow,out);

	        int responseCode = con.getResponseCode();
	        StringBuilder builder = new StringBuilder();

	    	InputStream in = con.getInputStream();
	        byte[] buffer = new byte[1024];
	        int count;
	        while((count = in.read(buffer))!=-1) {
	            builder.append(new String(buffer,0,count));
	        }
	        ret = builder.toString();
	        //System.out.println("Output: "+ret);
	        //LOG.info("Output: "+ret);
		} catch (Exception e) {
			LOG.error(e.getStackTrace());
			e.printStackTrace();
		}
        return ret;

    }
    
     /**
     * Returns the cluster services along with their status( running or not)
     * 
     * @author Vassilis Papaioannou
     * @param conf					the yarn configuration
     * @return String				the cluster services with their status
     */
    public static HashMap<String, String> issueRequestClusterStatus( YarnConfiguration conf) {
    	String masterDNS = conf.get( "yarn.resourcemanager.address").split(":")[0];
        String urlString = "http://" + masterDNS + ":1323/clusterStatus/services";
        String response = "";
        HashMap<String, String> services_n_status = new HashMap<String, String>();
		try {
	        //LOG.info("ClusterStatus issuing urlString: " + urlString);
			//System.out.println("ClusterStatus Issuing urlString: " + urlString);
	        URL url = new URL(urlString);
	        HttpURLConnection con = (HttpURLConnection) url.openConnection();

	        con.setRequestMethod("GET");

	        con.setRequestProperty("accept", "text/html");
	        con.setRequestProperty("Content-type", "text/html");
	        con.setDoInput(true);
	        
	        StringBuilder builder = new StringBuilder();

	    	InputStream in = con.getInputStream();
	        byte[] buffer = new byte[1024];
	        int count;
	        while((count = in.read(buffer))!=-1) {
	            builder.append(new String(buffer,0,count));
	        }
            //clean html response from its tags and replace them by a "_"
	        response = builder.toString().replaceAll( "<[^>]+>", "_");
            //due to starting and closing tags, the tokens of the remainder text will be
            //separated by a double "_" i.e. "__" that must be trimmed
            //remove leading and trailing double "_" and substitute the intermediate by a newline
            response = response.replaceAll( "^__", "").replaceAll( "__$", "").replaceAll( "__", "\n");
	        //LOG.info("Request response: " + services_n_status);
		} catch (Exception e) {
			LOG.error( e.getStackTrace());
			e.printStackTrace();
		}
		for( String servic: response.split( "\n")){
	        services_n_status.put( servic.split( ":")[ 0].trim(), servic.split( ":")[ 1].trim());
	    }
        return services_n_status;
    }
    
    /**
    * Replans the currently running workflow
    * 
    * @author Vassilis Papaioannou
    * @param conf					the yarn configuration
    * @param id						the id of running workflow
    */
    public static void issueRequestReplan( YarnConfiguration conf, String id) {
	   String masterDNS = conf.get( "yarn.resourcemanager.address").split(":")[0];
	   String urlString = "http://" + masterDNS + ":1323/runningWorkflows/replan/" + id;       
	   try {
	        //LOG.info("Replanning workflow " + id + " issuing urlString: " + urlString);
			//System.out.println("ClusterStatus Issuing urlString: " + urlString);
	        URL url = new URL(urlString);
	        HttpURLConnection con = (HttpURLConnection) url.openConnection();
	
	        con.setRequestMethod("GET");
	        con.setRequestProperty("accept", "application/xml");
	        con.setRequestProperty("Content-type", "application/xml");	        
	        con.setDoInput(true);
	        
	        StringBuilder builder = new StringBuilder();
	
	    	InputStream in = con.getInputStream();
	        byte[] buffer = new byte[1024];
	        int count;
	        while((count = in.read(buffer))!=-1) {
	            builder.append(new String(buffer,0,count));
	        }
	        //LOG.info( "Replan requested");
			} catch (Exception e) {
				LOG.error( e.getStackTrace());
				e.printStackTrace();
	   }
	   return;
   }
   
   /**
   * Returns the currently running workflow
   * 
   * @author Vassilis Papaioannou
  * @param conf						the yarn configuration
  * @param id						the id of running workflow
   */
  public static WorkflowDictionary issueRequestRunningWorkflow( YarnConfiguration conf, String id) throws Exception {
	  String masterDNS = conf.get( "yarn.resourcemanager.address").split(":")[0];
      String urlString = "http://" + masterDNS + ":1323/runningWorkflows/XML/" + id;
      StringBuilder builder = null;
      StringBuffer xmlStr = null;
      InputStream in = null;
      WorkflowDictionary running_workflow = null;
      JAXBContext jaxbContext = JAXBContext.newInstance( WorkflowDictionary.class );
      Unmarshaller u = jaxbContext.createUnmarshaller();
      try {
	        URL url = new URL(urlString);
	        HttpURLConnection con = (HttpURLConnection) url.openConnection();

	        con.setRequestMethod("GET");
	        con.setRequestProperty("accept", "application/XML");
	        con.setRequestProperty("Content-type", "application/XML");	        
	        con.setDoInput(true);
	        
	        builder = new StringBuilder();
	    	in = con.getInputStream();
	        byte[] buffer = new byte[1024];
	        int count;
	        while((count = in.read(buffer))!=-1) {
	            builder.append(new String(buffer,0,count));
	        }
	        xmlStr = new StringBuffer( builder.toString());
	        running_workflow = (WorkflowDictionary) u.unmarshal( new StreamSource( new StringReader( xmlStr.toString())));
		running_workflow.replaceDescription( "<br>", "\n");
	        //LOG.info( "Running workflow: " + running_workflow);
	} 
    catch (Exception e)
    {
		LOG.error( e.getStackTrace());
		e.printStackTrace();
    }
    return running_workflow;
  }   

  /**
  * Returns the replanned workflow
  * 
  * @author Vassilis Papaioannou
  * @param conf						the yarn configuration
  * @param id						the id of running workflow
  */
  public static WorkflowDictionary issueRequestToRunWorkflow( YarnConfiguration conf, String id) throws Exception {
	  String masterDNS = conf.get( "yarn.resourcemanager.address").split(":")[0];
      String urlString = "http://" + masterDNS + ":1323/runningWorkflows/toRunWorkflow/XML/" + id;
      StringBuilder builder = null;
      StringBuffer xmlStr = null;
      InputStream in = null;
      WorkflowDictionary to_run_workflow = null;
      JAXBContext jaxbContext = JAXBContext.newInstance( WorkflowDictionary.class );
      Unmarshaller u = jaxbContext.createUnmarshaller();
      try {
	        //LOG.info("To run workflow workflow " + id + " issuing urlString: " + urlString);
			//System.out.println("ClusterStatus Issuing urlString: " + urlString);
	        URL url = new URL(urlString);
	        HttpURLConnection con = (HttpURLConnection) url.openConnection();

	        con.setRequestMethod("GET");
	        con.setRequestProperty("accept", "application/XML");
	        con.setRequestProperty("Content-type", "application/XML");	        
	        con.setDoInput(true);
	        
	        builder = new StringBuilder();
	    	in = con.getInputStream();
	        byte[] buffer = new byte[1024];
	        int count;
	        while((count = in.read(buffer))!=-1) {
	            builder.append(new String(buffer,0,count));
	        }
	        xmlStr = new StringBuffer( builder.toString());
	        to_run_workflow = (WorkflowDictionary) u.unmarshal( new StreamSource( new StringReader( xmlStr.toString())));
		to_run_workflow.replaceDescription( "<br>", "\n");
	} 
    catch (Exception e)
    {
		LOG.error( e.getStackTrace());
		e.printStackTrace();
    }
    return to_run_workflow;
  }
  
  /**
  * Returns the application id of the currently running workflow
  * 
  * @author Vassilis Papaioannou
  * @param conf						the yarn configuration
  * @param id						the id of running workflow
  */
  public static int issueRequestApplicationId( YarnConfiguration conf, String id) throws Exception {
	  String masterDNS = conf.get( "yarn.resourcemanager.address").split(":")[0];
      String urlString = "http://" + masterDNS + ":1323/runningWorkflows/" + id + "/application/name";
      StringBuilder builder = null;
      InputStream in = null;
      int applicationId = 0;
      try {
	        URL url = new URL(urlString);
	        HttpURLConnection con = (HttpURLConnection) url.openConnection();

	        con.setRequestMethod("GET");
	        con.setRequestProperty("accept", "text/html");
	        con.setRequestProperty("Content-type", "text/html");	        
	        con.setDoInput(true);
	        
	        builder = new StringBuilder();
	    	in = con.getInputStream();
	        byte[] buffer = new byte[1024];
	        int count;
	        while((count = in.read(buffer))!=-1) {
	            builder.append(new String(buffer,0,count));
	        }
		urlString = builder.toString();
		urlString = urlString.substring( urlString.lastIndexOf( "_") + 1, urlString.lastIndexOf( "/"));
	        applicationId = Integer.parseInt( urlString);
	} 
    catch (Exception e)
    {
		LOG.error( e.getStackTrace());
		e.printStackTrace();
    }
    return applicationId;
  }
}
