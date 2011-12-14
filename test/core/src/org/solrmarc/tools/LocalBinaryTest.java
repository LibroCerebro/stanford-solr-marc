package org.solrmarc.tools;

import static org.junit.Assert.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.*;
import org.solrmarc.testUtils.*;

/**
 * based on Bob's old code in RemoteServerTest as of 12-12-2011
 *   needs refactoring to avoid using CommandLineUtil
 * @author Naomi Dushay
 */
public class LocalBinaryTest extends IndexTest
{
    /**
     * Start a Jetty driven solr server running in a separate JVM at port jetty.test.port
     *  and set the logging levels
     */
@BeforeClass
    public static void setupTestClass() 
    {
		setTestLoggingLevels();
    	startTestJetty();
    }

    /**
     * Stop the Jetty server we spun up for testing
     */
@AfterClass
    public static void stopJetty()
    {
    	stopTestJetty();
    	closeSolrProxy();
    }
    

@Test
	public void testSolrjBinaryAndNonBinaryBob()
	{
		initVarsForHttpTestIndexing();
		ByteArrayOutputStream out1 = new ByteArrayOutputStream();
		ByteArrayOutputStream err1 = new ByteArrayOutputStream();
		Map<String, String> addnlProps = new LinkedHashMap<String, String>();

		// Add several records using local binary solrj
		addnlProps.put("solrmarc.use_binary_request_handler", "true");
		addnlProps.put("solrmarc.use_streaming_proxy", "false");
		CommandLineUtils.runCommandLineUtil("org.solrmarc.marc.MarcImporter",
				"main", null, out1, err1, new String[] { testConfigFname, testDataParentPath + "/mergeInput.mrc" }, addnlProps);

		String results = getRawFieldByIDBob(testSolrUrl, "u3", "marc_display");
		assertTrue("Record added using remote non-binary request handler doesn't contain \\u001e", results.contains("\\u001e"));
		assertTrue("Record added using remote non-binary request handler does contain #30;", !results.contains("#30;"));

		// Add several records using local non-binary solrj
		out1.reset();
		err1.reset();
		addnlProps.put("solrmarc.use_binary_request_handler", "false");
		CommandLineUtils.runCommandLineUtil("org.solrmarc.marc.MarcImporter",
				"main", null, out1, err1, new String[] { testConfigFname, testDataParentPath + "/mergeInput.mrc" }, addnlProps);

		// Check whether record was not written as binary
		results = getRawFieldByIDBob(testSolrUrl, "u3", "marc_display");
		assertTrue("Record added using remote non-binary request handler does contain \\u001e", !results.contains("\\u001e"));
		assertTrue("Record added using remote non-binary request handler doesn't contain #30;",	results.contains("#30;"));

//		System.out.println("Test testSolrjBinaryAndNonBinary is successful");
	}

	/**
	 * getRawFieldByIDBob - Request record by id from Solr, and return the raw
	 *  value of the field. If the record doesn't exist id or the record
	 * doesn't contain that field return null
	 *  @param fieldOfInterest - the field from which we want the value 
	 */
	public static String getRawFieldByIDBob(String solrBaseUrl, String id, String fieldOfInterest)
	{
		String fieldValue = null;
		String selectStr = "select/?q=id%3A" + id + "&fl=" + fieldOfInterest + "&rows=1&wt=json";
		try
		{
			InputStream is = new URL(solrBaseUrl	+ "/" + selectStr).openStream();
			String solrResultStr = Utils.readStreamIntoString(is);
			String fieldLabel = "\"" + fieldOfInterest + "\":";
			int valStartIx = solrResultStr.indexOf(fieldLabel);
			int valEndIx = solrResultStr.indexOf("\"}]");
			if (valStartIx != -1 && valEndIx != -1)
				fieldValue = solrResultStr.substring(valStartIx + fieldLabel.length(), valEndIx);
		} 
		catch (MalformedURLException e)
		{
			e.printStackTrace();
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return (fieldValue);
	}

}
