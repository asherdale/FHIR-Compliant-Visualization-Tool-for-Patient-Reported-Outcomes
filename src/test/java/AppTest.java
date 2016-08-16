/*
 * Asher Dale
 * Summer 2016
 * Internship at BCH-CHIP
 */

import static java.lang.Math.toIntExact;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import junit.framework.TestCase;

// Testing
public class AppTest extends TestCase {
	
	App app = new App();
	
    public AppTest(String testName) {
        super(testName);
    }

    // Tests the parsing of QuestionnaireResponse files
    public void testFileParsing(File file) throws FileNotFoundException {
    	    	
    	FileReader fr = new FileReader(file);
    	ArrayList<ArrayList<Object>> test_data = app.parseQuestionnaireResponse(fr);
    	    	
    	assertEquals(test_data.get(0).get(1), "Asher!");
    	assertEquals(test_data.get(1).get(1), "Male");
    	assertEquals(test_data.get(2).get(1), "1999-07-31");
    	assertEquals(toIntExact((Long) test_data.get(3).get(1)), 17);
    	assertEquals(toIntExact((Long) test_data.get(4).get(1)), 2);
    	assertEquals(test_data.get(5).get(1), false);
    	assertEquals(test_data.get(6).get(1), "Steak");	
    }
    
    // Tests the uploading of QuestionnaireResponse files using a HTTP POST request
    public String testPopulate(CloseableHttpClient httpclient, File file) throws IOException {
    	    	
    	FileBody file_body = new FileBody(file);
    	
    	HttpEntity entity = MultipartEntityBuilder
    		    .create()
    		    .addPart("file", file_body)
    		    .addTextBody("index", "test")
    		    .addTextBody("type", "data")
    		    .build();
    	
    	String response = executeMultipartPOST(httpclient, entity);    
    	return response;
    }
    
    // Tests the creation of visualizations using a HTTP POST request
    public String testVisualize(CloseableHttpClient httpclient, File file) throws IOException {
    	
    	FileBody file_body = new FileBody(file);
    	FileBody vis_config_body = new FileBody(new File(AppConfig.getProp(AppConfig.PATH_TO_TEST_VIS_CONFIG)));
    	
    	HttpEntity entity = MultipartEntityBuilder
    		    .create()
    		    .addPart("file", file_body)
    		    .addPart("vis_config", vis_config_body)
    		    .addTextBody("index", "test")
    		    .build();
    	
    	String response = executeMultipartPOST(httpclient, entity);
    	return response;
    }
    
    // Method that tests the "delete" command using a HTTP POST request
    public String testDelete(CloseableHttpClient httpclient) throws IOException {
    	
    	String response = createPOSTRequest(httpclient, "delete", "test");
    	return response;
    }
    
    // Executes a Multipart POST request to the servlet (contains one or more files)
    public String executeMultipartPOST(CloseableHttpClient httpclient, HttpEntity entity) throws IOException {
    	
    	HttpPost httppost = new HttpPost("http://localhost:8080/visualize/fhir/QuestionnaireResponse");
    	httppost.setEntity(entity);
    	
    	return executePOSTRequest(httpclient, httppost);
    }
    
    // Creates a basic POST request to the servlet (the request is not Multipart)
    public String createPOSTRequest(CloseableHttpClient httpclient, String command, String index) throws IOException {
    	
    	ArrayList<NameValuePair> formparams = new ArrayList<NameValuePair>();
    	formparams.add(new BasicNameValuePair("command", command));
    	formparams.add(new BasicNameValuePair("index", index));
    	UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, Consts.UTF_8);
    	HttpPost httppost = new HttpPost("http://localhost:8080/visualize/fhir/QuestionnaireResponse");
    	httppost.setEntity(entity);
    	
    	return executePOSTRequest(httpclient, httppost);
    }
    
    // Executes a POST request and returns the servlet's response
    public String executePOSTRequest(CloseableHttpClient httpclient, HttpPost httppost) throws IOException {
    	
    	CloseableHttpResponse response = httpclient.execute(httppost);
    	try {
    		
    		BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

    		StringBuffer result = new StringBuffer();
    		String line = "";
    		while ((line = rd.readLine()) != null) {
    			result.append(line);
    		}
    		
    		return result.toString();
    		    		
    	} finally {
    	    response.close();
    	}
    }
    
    /*
     *  Where the actual tests are called.
     *  IMPORTANT NOTE: Within this method, only run one test-method at a time. Because Elasticsearch is a bit weird, the tests
     *  will not work if you call two test-methods at once. For example, if you call testPopulate and testDelete in the same call,
     *  the tests will not work like they are supposed to work.
     *  ALSO: Don't call testVisualize or testDelete without having already stored data in Elasticsearch by calling testPopulate
     */
    public void testApp() throws IOException {
    	
    	CloseableHttpClient httpclient = HttpClients.createDefault();
    	File test_file = new File(AppConfig.getProp(AppConfig.PATH_TO_TEST_DATA));
    	
    	testFileParsing(test_file);
    	
    	//String populate_response = testPopulate(httpclient, test_file);
    	//assertEquals(populate_response, "Uploaded ./questionnaire_response_test.json successfully");
    	
    	//String visualize_response = testVisualize(httpclient, test_file);
		//assertEquals(visualize_response, "Created visualizations successfully");
    	    	
		//String delete_response = testDelete(httpclient);
    	//assertEquals(delete_response, "Successfully deleted the data");
    }
}