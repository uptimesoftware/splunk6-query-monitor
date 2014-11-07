package com.uptimesoftware.uptime.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.PluginWrapper;

import com.splunk.Job;
import com.splunk.JobArgs;
import com.splunk.JobCollection;
import com.splunk.JobResultsArgs;
import com.splunk.Service;
import com.splunk.ServiceArgs;
import com.uptimesoftware.uptime.plugin.api.Extension;
import com.uptimesoftware.uptime.plugin.api.Plugin;
import com.uptimesoftware.uptime.plugin.api.PluginMonitor;
import com.uptimesoftware.uptime.plugin.monitor.MonitorState;
import com.uptimesoftware.uptime.plugin.monitor.Parameters;

/**
 * Splunk6 Query Monitor
 * 
 * @author uptime software
 */
public class SplunkSixQueryMonitor extends Plugin {

	/**
	 * Constructor - a plugin wrapper.
	 * 
	 * @param wrapper
	 */
	public SplunkSixQueryMonitor(PluginWrapper wrapper) {
		super(wrapper);
	}

	/**
	 * A nested static class which has to extend PluginMonitor.
	 * 
	 * Functions that require implementation :
	 * 1) The monitor function will implement the main functionality and should set the monitor's
	 * state and result message prior to completion.
	 * 2) The setParameters function will accept a Parameters object containing the values filled
	 * into the monitor's configuration page in Up.time.
	 */
	@Extension
	public static class UptimeSplunkSixQueryMonitor extends PluginMonitor {
		// Logger object.
		private static final Logger logger = LoggerFactory
				.getLogger(UptimeSplunkSixQueryMonitor.class);

		// monitor message.
		private String monitorMessage = "";

		// input params from up.time.
		private HashMap<String, Object> inputs = new HashMap<String, Object>();

		// constants
		static final String HOSTNAME = "hostname";
		static final String PORT = "port";
		static final String USERNAME = "username";
		static final String PASSWORD = "password";
		static final String SPLUNK_QUERY = "splunkQuery";
		static final String QUERY_COUNT = "queryCount";

		/**
		 * The setParameters function will accept a Parameters object containing the values filled
		 * into the monitor's configuration page in Up.time.
		 * 
		 * @param params
		 *            Parameters object which contains inputs.
		 */
		@Override
		public void setParameters(Parameters params) {
			logger.debug("Step 1 : Get inputs from Up.time and store them in HashMap.");
			// See definition in .xml file for plugin. Each plugin has different number of
			// input/output parameters.
			inputs.put(HOSTNAME, params.getString(HOSTNAME));
			inputs.put(PORT, params.getInteger(PORT));
			inputs.put(USERNAME, params.getString(USERNAME));
			inputs.put(PASSWORD, params.getString(PASSWORD));
			inputs.put(SPLUNK_QUERY, params.getString(SPLUNK_QUERY));
		}

		/**
		 * The monitor function will implement the main functionality and should set the monitor's
		 * state and result message prior to completion.
		 */
		@Override
		public void monitor() {
			logger.debug("Check if splunkQuery is specified or not");
			if (inputs.get(SPLUNK_QUERY) == null || ((String) inputs.get(SPLUNK_QUERY)).isEmpty()) {
				monitorMessage = "No Splunk Query specified.";
				setStateAndMessage(MonitorState.WARN, monitorMessage);
				return;
			}

			logger.debug("Connect to a given Splunk server.");
			Service service = connectToSplunkServer(inputs);

			logger.debug("Get JobCollection of current service.");
			JobCollection jobCollection = service.getJobs();

			logger.debug("Check if there is any job in the service.");
			if (jobCollection.size() <= 0) {
				monitorMessage = "There is no job in the service.";
				setStateAndMessage(MonitorState.UNKNOWN, monitorMessage);
				return;
			}

			logger.debug("Search the JobCollection with a given Splunk query.");
			Job job = createSearch(inputs, jobCollection);

			logger.debug("Check if the search has failed / paused / etc");
			if (job.isFailed()) {
				monitorMessage = "Searching the JobCollection failed.";
				setStateAndMessage(MonitorState.UNKNOWN, monitorMessage);
				return;
			} else if (job.isPaused()) {
				monitorMessage = "Searching the JobCollection paused.";
				setStateAndMessage(MonitorState.UNKNOWN, monitorMessage);
				return;
			} else if (!job.isReady()) {
				monitorMessage = "The search Job is not ready to return output data.";
				setStateAndMessage(MonitorState.UNKNOWN, monitorMessage);
				return;
			}

			String fullResults = outputResultsJSON(inputs, job);
			if (fullResults == null) {
				monitorMessage = "Failed to get full results for debugging mode.";
				setStateAndMessage(MonitorState.UNKNOWN, monitorMessage);
				return;
			}
			logger.debug("Full result : " + fullResults);

			addVariable(QUERY_COUNT, job.getResultCount());

			setStateAndMessage(MonitorState.OK, "Monitor ran successfully.");
		}

		/**
		 * Connect to Splunk server with inputs from Up.time.
		 * 
		 * @param inputs
		 *            Inputs from Up.time.
		 * @return Service instance for retrieving JobCollection. JobCollection will be used to
		 *         create Job search with search query String.
		 */
		Service connectToSplunkServer(HashMap<String, Object> inputs) {
			ServiceArgs loginArgs = new ServiceArgs();
			loginArgs.setUsername((String) inputs.get(USERNAME));
			loginArgs.setPassword((String) inputs.get(PASSWORD));
			loginArgs.setHost((String) inputs.get(HOSTNAME));
			loginArgs.setPort((Integer) inputs.get(PORT));

			// Create a Service instance with specific ServiceArgs.
			return Service.connect(loginArgs);
		}

		/**
		 * Create search with a given Splunk query String and JobArgs.ExecutionMode.BLOCKING.
		 * 
		 * @param inputs
		 *            Inputs from Up.time.
		 * @param jobCollection
		 *            JobCollection of current service.
		 * @return Job instance which contains search results.
		 */
		Job createSearch(HashMap<String, Object> inputs, JobCollection jobCollection) {
			// Wait until the job search is done.
			JobArgs jobArgs = new JobArgs();
			jobArgs.setExecutionMode(JobArgs.ExecutionMode.BLOCKING);

			// Create search with a given Splunk query String and JobArgs.
			return jobCollection.create("search " + (String) inputs.get(SPLUNK_QUERY), jobArgs);
		}

		/**
		 * Output results in JSON format String using Jackson lib.
		 * 
		 * @param inputs
		 *            Inputs from Up.time.
		 * @param job
		 *            Job instance that contains search results.
		 * @return String output of 'results' field.
		 */
		String outputResultsJSON(HashMap<String, Object> inputs, Job job) {
			// Output JSON.
			JobResultsArgs resultsArgs = new JobResultsArgs();
			resultsArgs.setOutputMode(JobResultsArgs.OutputMode.JSON);

			InputStream resultStream = job.getResults(resultsArgs);
			BufferedReader resultReader = new BufferedReader(new InputStreamReader(resultStream));

			// Using the core's Jackson lib to convert Java object to JSON.
			String resultsField = null;
			try {
				ObjectMapper objectMapper = new ObjectMapper();
				JsonNode rootNode = objectMapper.readValue(resultReader, JsonNode.class);

				// Only get contents of "results" field.
				JsonNode resultsFieldNode = rootNode.get("results");

				resultsField = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
						resultsFieldNode);

				resultReader.close();
				resultStream.close();
			} catch (JsonParseException e) {
				monitorMessage = "Error while parsing results. " + e.getMessage();
				logger.error(monitorMessage);
			} catch (JsonMappingException e) {
				monitorMessage = "Error while mapping JSON results. " + e.getMessage();
				logger.error(monitorMessage);
			} catch (IOException e) {
				monitorMessage = "Input/Output error. " + e.getMessage();
				logger.error(monitorMessage);
			}

			return resultsField;
		}
	}
}