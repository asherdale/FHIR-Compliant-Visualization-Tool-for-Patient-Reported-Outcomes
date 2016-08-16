/*
 * Asher Dale
 * Summer 2016
 * Internship at BCH-CHIP
 */

import static java.lang.Math.toIntExact;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class App {
	
	JSONParser parser = new JSONParser();
	Client client = null;
	
	public App() {
			
		try {
			// Connects to Elasticsearch
			client = TransportClient.builder().build().addTransportAddress(new InetSocketTransportAddress(
			       		InetAddress.getByName(AppConfig.getProp(AppConfig.SERVER)),
			       		Integer.parseInt(AppConfig.getProp(AppConfig.PORT))));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// Populates Elasticsearch with the given file
	public String populateFromFile(File file, String index, String type) {

		if (file.length() == 0) {
			return "File is empty";
		} else if (!file.getName().endsWith("json")) {
			return "\"" + file + "\" is not a JSON file.";
		}

		FileReader fr = null;
		try {
			fr = new FileReader(file);
		} catch (FileNotFoundException e) {
			return e.toString();
		}

		// Prepares the data from the QuestionnaireResponse to be entered into Elasticsearch
		ArrayList<ArrayList<Object>> survey_data = parseQuestionnaireResponse(fr);
		XContentBuilder json = makeJSONObject(survey_data);
		
		if (survey_data.size() == 0) {
			return "Cannot parse the file \"" + file + "\"";
		}
				
		// Create an index-pattern in Kibana if one doesn't already exist
		checkForIndexPattern(survey_data, index);
		
		// Checks to see if a template should be created for the given index
		checkForTemplate(survey_data, index);
		
		// Indexes the data into Elasticsearch
		client.prepareIndex(index, type).setSource(json).get();

		return "Uploaded \"" + file + "\" successfully.";
	}
	
	//Automatically creates visualizations based on a visualization configuration file
	public String visualizeData(String index, File vis_config, File example_data) throws IOException {
		
		String response = "";
		
		if (vis_config.length() == 0) {
			return "The vis_config file is empty";
		} else if (example_data.length() == 0) {
			return "The example_data file is empty";
		} else if (!vis_config.getName().endsWith("json")) {
			return "\"" + vis_config + "\" is not a JSON file.";
		} else if (!example_data.getName().endsWith("json")) {
			return "\"" + example_data + "\" is not a JSON file.";
		}
		
		// Retrieves the linkId of each question by referring to an example of the survey
		FileReader fr = new FileReader(example_data);
		ArrayList<ArrayList<Object>> sample_survey_data = parseQuestionnaireResponse(fr);
		
		if (sample_survey_data.size() == 0) {
			return "Cannot parse the file \"" + example_data + "\"";
		}
		
		JSONObject jsonObject = null;
		try {
			jsonObject = (JSONObject) parser.parse(new FileReader(vis_config));
		} catch (ParseException e) {
			return "Cannot parse the file \"" + vis_config + "\"";
		}
		
		ArrayList<String> questions = new ArrayList<String>();
		for (ArrayList<Object> question_data: sample_survey_data) {
			questions.add((String) question_data.get(0));
		}
		
		Object[] links = jsonObject.keySet().toArray();
		for (Object link: links) {
			boolean created = false;
			for (ArrayList<Object> question_data: sample_survey_data) {
				// If the linkId of a question matches a linkId in the vis_config file, a visualization is made
				if (link.equals(question_data.get(question_data.size()-1))) {
					String question = (String) question_data.get(0);
					response += createVisualization(index, question, (JSONObject) jsonObject.get(link), questions);
					created = true;
				}
			}
			if (! created) {
				response += "The linkId \"" + link + "\" did not match any questions in the index \"" + index + "\"\n";
			}
		}
		return response;
	}
	
	// Deletes the specified index in Elasticsearch, along with its index-pattern
	public String delete(String index){
		
		client.admin().indices().delete(new DeleteIndexRequest(index)).actionGet();
		client.prepareDelete(".kibana", "index-pattern", index).get();
    	return "Successfully deleted the data";
	}
	
	//Searches Elasticsearch for all QuestionnaireResponse entries in the specified index (and specified type if given)
	public SearchResponse search(String index, String type){
		
		SearchResponse search = null;
		if (type == null || type == "") {
			search = client.prepareSearch(index).execute().actionGet();
		} else {
			search = client.prepareSearch(index).setTypes(type).execute().actionGet();
		}
		return search;
	}
	
	// Allows the user to export the data from an index into a CSV file
	// IMPORTANT NOTE: Before calling this, you must change "path_to_csv_exports" in the config.properties file
	public String exportToCSV(String fileName, String index, String type) {
		String delimiter = ",";
		String new_line_separator = "\n";
		
		FileWriter fileWriter = null;

		JSONArray questionnaire_responses = getSurveyData(index, type);
		if (questionnaire_responses.size() == 0) {
			return "No data found";
		}
		
		try {
			String path_to_file = AppConfig.getProp(AppConfig.PATH_TO_CSV_EXPORTS) + fileName + ".csv";
			File file = new File(path_to_file);			
			if (file.exists()) {
				fileWriter = new FileWriter(file);
			} else {
				fileWriter = new FileWriter(path_to_file);
			}
				
			for (int i = 0; i < questionnaire_responses.size(); i++) {
				
				JSONObject inner_hits_obj = (JSONObject) questionnaire_responses.get(i);
				JSONObject source = (JSONObject) inner_hits_obj.get("_source");
				Object[] keys = source.keySet().toArray();
				
				// Makes the first row of the CSV file (contains each question on the survey)
				if (i == 0) {
					fileWriter.append("ID");
					fileWriter.append(delimiter);
					for (int j = 0; j < source.size(); j++) {
						String question = keys[j].toString();
						if (question.contains(",")) {
							question = "\"" + question + "\"";
						}
						fileWriter.append(question);
						fileWriter.append(delimiter);
					}
					fileWriter.append(new_line_separator);
				}
				
				fileWriter.append((String) inner_hits_obj.get("_id"));
				fileWriter.append(delimiter);
				
				// Each of the following rows will contain the answers from one survey
				for (int j = 0; j < source.size(); j++) {
									
					String answer = source.get(keys[j]).toString();
					if (answer.contains(",")) {
						answer = "\"" + answer + "\"";
					}
					fileWriter.append(answer);
					fileWriter.append(delimiter);
				}
				fileWriter.append(new_line_separator);
			}
			return "Successfully exported to .csv file at: " + path_to_file;			
		} catch (IOException e) {
			return "There was an error during the export to a .csv file";
		} finally {
			try {
				fileWriter.flush();
				fileWriter.close();
			} catch (IOException e) {
				return "There was an error during the export to a .csv file";
			}
		}	
	}
	
	// Generates a visualization in Kibana
	public String createVisualization(String index, String question, JSONObject vis_config, ArrayList<String> questions) {
		
		String response = "";
		
		Object[] types = vis_config.keySet().toArray();
		for (Object type: types) {
			
			// Gets information about the visualization (type, name, etc.)
			String vis_type = (String) type;
			JSONObject vis_info = (JSONObject) vis_config.get(vis_type);
			
			// Generates a visualization based on the type of visualization
			String visState = "";
			if (vis_type.startsWith("line")) {
				visState = createLineGraph(question, vis_info);
			} else if (vis_type.startsWith("bar")) {
				visState = createBarGraph(question, vis_info);
			} else if (vis_type.startsWith("pie")) {
				visState = createPieChart(question, vis_info);
			} else {
				response += "The visualization type \"" + vis_type + "\" is not supported.\n";
				continue;
			}
			
			// Gets the name of the visualization, specified in the visual_config file
			String name = (String) vis_info.get("name");
			if (name == null) {
				response += "A visualization with the type \"" + vis_type + "\" is missing a name.\n";
				continue;
			}
			
			String splitBy = (String) vis_info.get("splitBy");
			if (splitBy != null) {
				if (! questions.contains(splitBy)) {
					response += "\"" + name + "\": The value entered in the \"splitBy\" field is not valid.\n";
					continue;
				}
			}
			
			// Error checking
			if (visState.length() <= 130) {
				response += "\"" + name + "\": " + visState + ".\n";
				continue;
			}

			// Creates a Kibana visualization in JSON
			JSONObject visualization_json = new JSONObject();
			visualization_json.put("title", name);
			visualization_json.put("version", 1);
			visualization_json.put("description", "");
			visualization_json.put("uiStateJSON", "{}");
			JSONObject new_obj = new JSONObject();
			new_obj.put("searchSourceJSON", "{\"index\":\"" + index + "\",\"query\":{\"query_string\":{\"query\":\"*\",\"analyze_wildcard\":true}},\"filter\":[]}");
			visualization_json.put("kibanaSavedObjectMeta", new_obj);
			visualization_json.put("visState", visState);

			// Stores the visualization in Kibana
			client.prepareIndex(".kibana", "visualization", name).setSource(visualization_json.toString()).get();
			response += "The visualization \"" + name + "\" was successfully generated.\n";
		}
		return response;
	}
	
	// Generates the code to make a line graph in Kibana
	public String createLineGraph(String question, JSONObject vis_info) {
		
		Object[] fields = vis_info.keySet().toArray();
		for (Object field: fields) {
			if (!(field.equals("name") || field.equals("interval") || field.equals("aggregation") || field.equals("splitBy"))) {
				return "The field \"" + field + "\" is not a valid field for line graphs";
			}
		}
		
		String visState = "{\"title\":\"\",\"type\":\"line\",\"params\":{\"shareYAxis\":true,\"addTooltip\":true,\"addLegend\":true,\"showCircles\":true,\"smoothLines\":false,\"interpolate\":\"linear\",\"scale\":\"linear\",\"drawLinesBetweenPoints\":true,\"radiusRatio\":9,\"times\":[],\"addTimeMarker\":false,\"defaultYExtents\":false,\"setYExtents\":false,\"yAxis\":{}},\"aggs\":[";
		
		String agg = null;
		if (vis_info.get("aggregation") != null) {
			agg = (String) vis_info.get("aggregation");
			agg = agg.toLowerCase();
			if (! (agg.equals("avg" ) || agg.equals("median") || agg.equals("min" ) || agg.equals("max" ) || agg.equals("sum" ) || agg.equals("std_dev" ))) {
				return "The aggregation \"" + agg + "\" is not a valid aggregation for line graphs";
			}
		} else {
			agg = "avg";
		}
		
		visState += "{\"id\":\"1\",\"type\":\""
				+ agg + "\",\"schema\":\"metric\",\"params\":{\"field\":\""
				+ question + "\"";
		if (agg.equals("median")) {
			visState += ",\"percents\":[50]";
		}
		visState += "}}";
		
		String interval = (String) vis_info.get("interval");
		if (interval.equals("y") || interval.equals("M") || interval.equals("w") || interval.equals("d") || interval.equals("auto")) {
			visState += ",{\"id\":\"2\",\"type\":\"date_histogram\",\"schema\":\"segment\",\"params\":{\"field\":\"Date\",\"interval\":\""
					+ interval + "\",\"customInterval\":\"2h\",\"min_doc_count\":1,\"extended_bounds\":{}}}";
		} else {
			return "The interval \"" + interval + "\" is not a valid interval";
		}
		
		String splitBy = (String) vis_info.get("splitBy");
		if (splitBy != null) {
			visState += ",{\"id\":\"3\",\"type\":\"terms\",\"schema\":\"group\",\"params\":{\"field\":\""
					+ splitBy + "\",\"size\":50,\"order\":\"desc\",\"orderBy\":\"_term\"}}";
		}		
		visState += "],\"listeners\":{}}";

		return visState;
	}
	
	// Generates the code to make a bar graph in Kibana
	public String createBarGraph(String question, JSONObject vis_info) {
		
		Object[] fields = vis_info.keySet().toArray();
		for (Object field: fields) {
			if (!(field.equals("name") || field.equals("ranges") || field.equals("aggregation") || field.equals("splitBy"))) {
				return "The field \"" + field + "\" is not a valid field for bar graphs";
			}
		}
				
		String visState = "{\"title\":\"\",\"type\":\"histogram\",\"params\":{\"addLegend\":true,\"addTimeMarker\":false,\"addTooltip\":true,\"defaultYExtents\":false,\"mode\":\"stacked\",\"scale\":\"linear\",\"setYExtents\":false,\"shareYAxis\":true,\"times\":[],\"yAxis\":{}},\"aggs\":";
	
		String agg = (String) vis_info.get("aggregation");	
		if (agg == null && vis_info.get("splitBy") != null) {
			return "If you have entered a value for the \"splitBy\" field, you must enter in a value for the \"aggregation\" field as well";
		} else if (agg != null && vis_info.get("splitBy") == null) {
			return "If you have entered a value for the \"aggregation\" field, you must enter in a value for the \"splitBy\" field as well";
		}
		
		if (agg == null) {
			visState += "[{\"id\":\"1\",\"type\":\"count\",\"schema\":\"metric\",\"params\":{}}";
			if (vis_info.get("ranges") != null) {
				String range_json = getJSONRangeString((String) vis_info.get("ranges"));
				if (!range_json.startsWith("{")) {
					return range_json;
				}
				visState += ",{\"id\":\"2\",\"type\":\"range\",\"schema\":\"segment\",\"params\":{\"field\":\""
					+ question + "\",\"ranges\":["
					+ range_json + "]}}],\"listeners\":{}}";
			} else {
				visState += ",{\"id\":\"2\",\"type\":\"terms\",\"schema\":\"segment\",\"params\":{\"field\":\""
						+ question + "\",\"size\":50,\"order\":\"desc\",\"orderBy\":\"1\"}}],\"listeners\":{}}";
			}
		} else {
			if (vis_info.get("ranges") != null) {
				return "One cannot make a bar graph using all of the following fields at the same time: \"ranges\", \"splitPie\", and \"aggregation\"";
			}
			if (! (agg.equals("avg" ) || agg.equals("median") || agg.equals("min" ) || agg.equals("max" ) || agg.equals("sum" ))) {
				return "The aggregation \"" + agg + "\" is not a valid aggregation for bar graphs";
			}
			visState += "[{\"id\":\"1\",\"type\":\""
					+ agg.toLowerCase() + "\",\"schema\":\"metric\",\"params\":{\"field\":\""
					+ question + "\"";
			if (agg.toLowerCase().equals("median")) {
				visState += ",\"percents\":[50]";
			}
			visState += "}},{\"id\":\"2\",\"type\":\"terms\",\"schema\":\"segment\",\"params\":{\"field\":\""
					+ vis_info.get("splitBy").toString() + "\",\"size\":50,\"order\":\"desc\",\"orderBy\":\"_term\"}}],\"listeners\":{}}";
		}
		return visState;
	}
	
	// Generates the code to make a pie chart in Kibana
	public String createPieChart(String question, JSONObject vis_info) {
		
		Object[] fields = vis_info.keySet().toArray();
		for (Object field: fields) {
			if (!(field.equals("name") || field.equals("ranges") || field.equals("splitBy"))) {
				return "The field \"" + field + "\" is not a valid field for pie charts";
			}
		}
		
		String visState = "{\"title\":\"\",\"type\":\"pie\",\"params\":{\"shareYAxis\":true,\"addTooltip\":true,\"addLegend\":true,\"isDonut\":true},\"aggs\":[{\"id\":\"1\",\"type\":\"count\",\"schema\":\"metric\",\"params\":{}},{\"id\":\"2\",\"type\":\"";
		
		if (vis_info.get("ranges") != null) {
			if (vis_info.get("splitBy") != null) {
				return "Pie charts with both of the fields \"ranges\" and \"splitBy\" are not supported";
			}
			String range_json = getJSONRangeString((String) vis_info.get("ranges"));
			if (!range_json.startsWith("{")) {
				return range_json;
			}
			visState += "range\",\"schema\":\"segment\",\"params\":{\"field\":\""
					+ question + "\",\"ranges\":["
					+ range_json + "]}}],\"listeners\":{}}";
		} else {
			if (vis_info.get("splitBy") != null) {
				visState += "terms\",\"schema\":\"segment\",\"params\":{\"field\":\""
						+ vis_info.get("splitBy") + "\",\"size\":5,\"order\":\"desc\",\"orderBy\":\"1\"}},{\"id\":\"3\",\"type\":\"";
			} 
			visState += "terms\",\"schema\":\"segment\",\"params\":{\"field\":\""
					+ question + "\",\"size\":50,\"order\":\"desc\",\"orderBy\":\"1\"}}],\"listeners\":{}}";
		}
		return visState;
	}
	
	/*
	 * Given a FHIR QuestionnaireResponse resource in a JSON file,
	 * this method returns a list of smaller lists, each containing a question from the survey with its answer and linkId
	 */
	public ArrayList<ArrayList<Object>> parseQuestionnaireResponse(FileReader fr){
		
		ArrayList<ArrayList<Object>> survey_data = new ArrayList<ArrayList<Object>>();
		
		try {
			// Utilizes the JSON-simple toolkit to begin parsing and digging into the .json file
			JSONObject jsonObject = (JSONObject) parser.parse(fr);
			
			// If applicable, retrieves the Patient that filled out the survey
			JSONObject subject = (JSONObject) jsonObject.get("subject");			
			if (subject != null) {
				String patient_link = (String) subject.get("reference");
				if (patient_link != null) {
					ArrayList<Object> patient_data = new ArrayList<Object>();
					patient_data.add("Patient");
					patient_data.add(patient_link);
					survey_data.add(patient_data);
				}
			}
			
			// If applicable, retrieves the date that the survey was filled out
			String authored = (String) jsonObject.get("authored");
			if (authored != null) {
				ArrayList<Object> date_data = new ArrayList<Object>();
				date_data.add("Date");
				date_data.add(authored);
				survey_data.add(date_data);
			}
			
			JSONObject group = (JSONObject) jsonObject.get("group");
			if (group == null) {
				return new ArrayList<ArrayList<Object>>();
			}
			
			// Continues to dig into the QuestionnaireResponse by utilizing a recursive method that sifts through unimportant data
			if (group.containsKey("question")) {
				parseJSONArray((JSONArray) group.get("question"), survey_data);
			} else if (group.containsKey("group")) {
				parseJSONArray((JSONArray) group.get("group"), survey_data);
			} else {
				return new ArrayList<ArrayList<Object>>();
			}
			
			return survey_data;
			
		} catch (IOException e) {
			return new ArrayList<ArrayList<Object>>(); 
		} catch (ParseException e) {
			return new ArrayList<ArrayList<Object>>();
		}
	}
	
	
	// Recursively parses through JSON objects and arrays within the QuestionnaireResponse
	// until it finds all of the questions, answers, and linkIDs
	public void parseJSONArray(JSONArray array, ArrayList<ArrayList<Object>> survey_data) {
		
		for (Object obj: array) {
			
			JSONObject jObj = (JSONObject) obj;
			if (jObj.containsKey("question")) {
		
				parseJSONArray((JSONArray) jObj.get("question"), survey_data);
				
			} else if (jObj.containsKey("group")) {
				
				parseJSONArray((JSONArray) jObj.get("group"), survey_data);
				
			} else if (jObj.containsKey("answer")){
				
				// Once the method has found an answer, it finds the answer's corresponding question and linkId
				String linkId = (String) jObj.get("linkId");
				String question_text = (String) jObj.get("text");

				// Finds the value of the answer
				JSONArray answer = (JSONArray) jObj.get("answer");
				JSONObject inner_answer = (JSONObject) answer.get(0);
				String valueType = (String) inner_answer.keySet().toArray()[0];
				Object answer_obj = (Object) inner_answer.get(valueType);
				
				// Finds the answer value in the case that the answer is more complex than just a string or number
				if (answer_obj.getClass().toString().equals("class org.json.simple.JSONObject")) {
					
					JSONObject answer_jObj = (JSONObject) answer_obj;
					if (answer_jObj.containsKey("display")) {
						answer_obj = answer_jObj.get("display");
					} else if (answer_jObj.containsKey("code")) {
						answer_obj = answer_jObj.get("code");
					} else {
						System.out.println("This program cannot identify the answer from this JSON object: " + answer_obj);
					}
				}
								
				// Saves the desired data in a small list, which is then put into the larger list
				ArrayList<Object> answer_data = new ArrayList<Object>();
				answer_data.add(question_text);
				answer_data.add(answer_obj);
				answer_data.add(linkId);
				survey_data.add(answer_data);
				
				// Continues to find more answers if there are conditional questions based on this answer
				if (inner_answer.containsKey("group")) {
					parseJSONArray((JSONArray) inner_answer.get("group"), survey_data);
				} else if (inner_answer.containsKey("question")) {
					parseJSONArray((JSONArray) inner_answer.get("question"), survey_data);
				}
			}
		}		
	}
	
	//Creates an JSON object that can be indexed into Elasticsearch
	public XContentBuilder makeJSONObject(ArrayList<ArrayList<Object>> arrayList) {
		try {
			
			XContentBuilder json = jsonBuilder().startObject();
			
			//Populates the JSON object with questions as the keys and answers as their values
			for (ArrayList<Object> data: arrayList){
				json.field((String) data.get(0), data.get(1));	
			}
			json.endObject();
			return json;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	// Formats the desired ranges for a visualization into JSON that can be used in the Kibana visualization JSON
	public String getJSONRangeString(String range_str) {

		String[] ranges = range_str.split(",");
		if (ranges.length == 0) {
			return "The value in the \"ranges\" field is not supported";
		}
		
		String range_json = "";
		for (String range: ranges) {
			String[] split = range.split("-");
			if (split.length < 2) {
				return "The value in the \"ranges\" field is not supported";
			}
			String minimum = split[0];
			String maximum = split[1];
			if (!(NumberUtils.isNumber(minimum) && NumberUtils.isNumber(maximum))){
				return "The value in the \"ranges\" field is not supported";
			}	
			range_json += "{\"from\":" + minimum + ",\"to\":" + maximum + "},";
		}
		range_json = range_json.substring(0, range_json.length()-1);
				
		return range_json;
	}
	
	// Checks if an index-pattern exists for the given index
	public void checkForIndexPattern(ArrayList<ArrayList<Object>> survey_data, String index) {
		
		boolean index_pattern_exists = false;
		JSONArray search = getSurveyData(".kibana", "index-pattern");
		
		// Checks whether an index-pattern exists for the given index
		for (int i = 0; i < search.size(); i++) {
			JSONObject index_pattern = (JSONObject) search.get(i);
			JSONObject source = (JSONObject) index_pattern.get("_source");
			if (index.equals(source.get("title"))) {
				index_pattern_exists = true;
			}
		}
		
		// Calls a method to create an index-pattern if one doesn't already exist for the given index
		if (! index_pattern_exists) {
			makeIndexPattern(survey_data, index);
		}
	}
	
	// Creates an index-pattern in Kibana, enabling visualizations to be made
	public void makeIndexPattern(ArrayList<ArrayList<Object>> survey_data, String index) {

		JSONObject sourceObject = new JSONObject();
		sourceObject.put("title", index);

		String fields = "[{\"name\":\"_index\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"_source\",\"type\":\"_source\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"_id\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"_type\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"_score\",\"type\":\"number\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false}";

		for (ArrayList<Object> question : survey_data) {

			String question_text = question.get(0).toString();
			String type = question.get(1).getClass().toString();
	
			if (question_text.equals("Date")){
				type = "date";
			} else if (type.equals("class java.lang.Long")) {
				type = "number";
			} else if (type.equals("class java.lang.String")) {
				type = "string";
			} else if (type.equals("class java.lang.Boolean")) {
				type = "boolean";
			}

			String question_field = null;
			if (type.equals("string")) {
				question_field = ",{\"name\":\"" + question_text + "\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":false,\"doc_values\":true}";
			} else {
				question_field = ",{\"name\":\"" + question_text + "\",\"type\":\"" + type	+ "\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":false,\"doc_values\":true}";
			}

			fields += (question_field);
		}
		fields += "]";

		sourceObject.put("fields", fields);
		client.prepareIndex(".kibana", "index-pattern", index).setSource(sourceObject.toString()).get();
	}
	
	// If entering this data into ES creates a new index, this creates a template for the index
	public void checkForTemplate(ArrayList<ArrayList<Object>> survey_data, String index) {
		
		String[] indices = client.admin().indices().prepareGetIndex().setFeatures().get().getIndices();
		for (int i = 0; i < indices.length; i++) {
			if (indices[i].equals(index)) {
				return;
			}
		}
		makeTemplate(survey_data, index);
	}

	// Creates a template for the given index so strings are not automatically parsed in ES
	public void makeTemplate(ArrayList<ArrayList<Object>> survey_data, String index) {
		
		String template_name = index + "_template";
		
		CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpPut putRequest = new HttpPut("http://localhost:9200/_template/" + template_name);
            
            JSONObject template = new JSONObject();
            JSONObject mapping_json = new JSONObject();
            JSONObject types_json = new JSONObject();
            JSONObject properties_json = new JSONObject();
            
            JSONObject question_json = new JSONObject();
            question_json.put("type", "string");
            question_json.put("index", "not_analyzed");
            
            for (ArrayList<Object> question_data: survey_data) {
            	if (question_data.get(0).equals("Date")) {
                    JSONObject date_json = new JSONObject();
                    date_json.put("type", "date");
            		properties_json.put(question_data.get(0), date_json);
            	}
            	else if (question_data.get(1).getClass().toString().equals("class java.lang.String")) {
            		properties_json.put(question_data.get(0), question_json);
            	}
            }
            
            types_json.put("properties", properties_json);
            mapping_json.put("*", types_json);
            template.put("mappings", mapping_json);
            template.put("template", index);
            
            StringEntity input = new StringEntity(template.toString());
            putRequest.setEntity(input);
            HttpResponse response = httpClient.execute(putRequest);
                        
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	// Returns the desired data from a SearchResponse object
	public JSONArray getDataFromSearch(SearchResponse search) {
		try {
			
			JSONObject json_search = (JSONObject) parser.parse(search.toString());
			JSONObject hits = (JSONObject) json_search.get("hits");
			return (JSONArray) hits.get("hits");
			
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	// Returns an array of JSONObjects after searching ES, each of which contain one data entry from ES
	public JSONArray getSurveyData(String index, String type) {
		
		SearchResponse search = search(index, type);
		JSONArray survey_data = getDataFromSearch(search);
		
		// Searches again if the first search didn't return all of the requested data
		if (survey_data.size() < search.getHits().getTotalHits()) {
			if (type != null) {
				search = client.prepareSearch(index)
						.setTypes(type)
						.setSize(toIntExact(search.getHits().getTotalHits()))
						.execute()
						.actionGet();
				survey_data = getDataFromSearch(search);
			} else {
				search = client.prepareSearch(index)
						.setSize(toIntExact(search.getHits().getTotalHits()))
						.execute()
						.actionGet();
				survey_data = getDataFromSearch(search);
			}
		}
		return survey_data;
	}
}
