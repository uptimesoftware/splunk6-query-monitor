package com.uptimesoftware.uptime.plugin;

import static org.junit.Assert.*;

import java.util.HashMap;

import org.junit.BeforeClass;
import org.junit.Test;

import com.splunk.Job;
import com.splunk.JobCollection;
import com.splunk.Service;
import com.uptimesoftware.uptime.plugin.SplunkSixQueryMonitor.UptimeSplunkSixQueryMonitor;

public class SplunkSixQueryMonitorTest {

	// input params from up.time.
	private static HashMap<String, Object> inputs = new HashMap<String, Object>();

	@BeforeClass
	public static void setUpBeforetest() {
		inputs.put(UptimeSplunkSixQueryMonitor.HOSTNAME, null);
		inputs.put(UptimeSplunkSixQueryMonitor.PORT, 8089);
		inputs.put(UptimeSplunkSixQueryMonitor.USERNAME, null);
		inputs.put(UptimeSplunkSixQueryMonitor.PASSWORD, null);
		inputs.put(UptimeSplunkSixQueryMonitor.SPLUNK_QUERY, "*");
	}

	@Test
	public void splunkQueryMonitorTest() {
		UptimeSplunkSixQueryMonitor testInstance = new UptimeSplunkSixQueryMonitor();
		Service service = testInstance.connectToSplunkServer(inputs);
		assertNotNull(service);
		assertEquals(
				"https://" + inputs.get(UptimeSplunkSixQueryMonitor.HOSTNAME) + ":"
						+ inputs.get(UptimeSplunkSixQueryMonitor.PORT), service.getPrefix());

		JobCollection jobCollection = service.getJobs();
		assertNotNull(jobCollection);
		assertFalse(jobCollection.isEmpty());
		assertEquals("/services/search/jobs", jobCollection.getPath());

		Job job = testInstance.createSearch(inputs, jobCollection);
		assertNotNull(job);
		assertTrue(job.isReady());
		assertTrue(job.isDone());
		assertEquals("search " + inputs.get(UptimeSplunkSixQueryMonitor.SPLUNK_QUERY),
				job.getSearch());

		String fullResults = testInstance.outputResultsJSON(inputs, job);
		assertNotNull(fullResults);

		// System.out.println(job.getResultCount());
		// System.out.println(fullResults);
	}
}
