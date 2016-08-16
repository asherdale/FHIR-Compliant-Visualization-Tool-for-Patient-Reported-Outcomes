/*
 * Asher Dale
 * Summer 2016
 * Internship at BCH-CHIP
 */

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.elasticsearch.action.search.SearchResponse;
import com.oreilly.servlet.MultipartRequest;

public class Servlet extends HttpServlet {
	
	App app = null;
	
	// Initializes the Servlet and connects to Elasticsearch
	public void init() throws ServletException {
		app = new App();
	}
	
	// Called in response to a HTTP POST Request
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		// Checks if the the POST request contains a file (MultipartRequest)
		if (!request.getParameterNames().hasMoreElements()) {
			
			MultipartRequest file_request = new MultipartRequest(request, ".");
			File file = file_request.getFile("file");
			File vis_config = file_request.getFile("vis_config");
			
			String index = file_request.getParameter("index");
			String type = file_request.getParameter("type");
			
			// Error checking
			if (file == null) {
				if (vis_config == null) {
					out.println("File not given");
				} else {
					out.println("Example file not given");
				}
				return;
			} else if (index == null) {
				out.println("Index not given");
				return;
			} else if (type == null && vis_config == null) {
				out.println("Type not given");
				return;
			} else if (! index.equals(index.toLowerCase())) {
				out.println("Index can only contain lowercase characters");
				return;
			}
			
			if (vis_config == null) {
				
				// Enters the data from the QuestionnaireResponse .json file into Elasticsearch
				out.println(app.populateFromFile(file, index, type));
				
			} else {
				
				// Creates visualizations based on the vis_config file
				out.println(app.visualizeData(index, vis_config, file));
				vis_config.delete();
			}
			file.delete();
		}
		// If the POST request does not contain a file, then the servlet checks if it is a command
		else {
			String index = request.getParameter("index");
			String type = request.getParameter("type");
			String command = request.getParameter("command");

			// Error checking
			if (index == null) {
				out.println("Index not given");
				return;
			} else if (command == null) {
				out.println("Command not given");
				return;	
			} else if (! index.equals(index.toLowerCase())) {
				out.println("Indexes can only contain lowercase characters");
				return;
			}
			
			// Makes sure that the index exists
			String[] indices = app.client.admin().cluster().prepareState().execute().actionGet().getState().getMetaData().concreteAllIndices();
			boolean index_exists = false;
			for (String ind: indices) {
				if (index.equals(ind)) {
					index_exists = true;
				}
			}
			if (! index_exists) {
				out.println("The index \"" + index + "\" does not exist");
				return;
			}
			
			
			// Deletes all of the data from the given index with the given type
			if (command.equals("delete")) {
				
				out.println(app.delete(index));
			}
			// Exports the data from the given index to a CSV file (can also be given a type to further specify)
			else if (command.equals("csv")) {
				
				String fileName = request.getParameter("fileName");
				if (fileName == null) {
					out.println("Name of csv file not given");
				} else {
					out.println(app.exportToCSV(fileName, index, type));
				}
			}
			// Conducts a search of all of the data in the given index (can also be given a type to search)
			else if (command.equals("search")) {
				
				SearchResponse search = app.search(index, type);
				if (search.getHits().getTotalHits() == 0) {
					out.println("No data found");
				} else {
					out.println(search);
				}
			} else {
				out.println("The command \"" + command + "\" does not exist");
			}
			
			
		}
		
	}
	
	// Called in response to a HTTP GET request (for testing purposes)
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
	
		// Basic webpage for testing purposes
		response.setContentType("text/html");
	    PrintWriter out = response.getWriter();
	    out.println("<h1><center>FHIR-compliant Visualization Tool for Patient Reported Outcomes</center></h1>\n" +
	    "<p><center>By Asher Dale</center></p>");	
	}
	
	// Closes the connection to Elasticsearch when destroying the Servlet
	public void destroy() {
		app.client.close();
	}
}