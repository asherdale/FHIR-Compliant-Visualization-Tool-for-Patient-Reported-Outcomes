# FHIR-Compliant Visualization Tool for Patient Reported Outcomes
*FHIR-compliant Visualization Tool for Patient Reported Outcomes* is a lightweight storage and visualization software for PROs entered as surveys. It is composed of a  web-based visualization app powered by Kibana ([https://www.elastic.co/products/kibana](https://www.elastic.co/products/kibana)), a storage unit based on Elastic Search ([https://www.elastic.co/](https://www.elastic.co/)) and a REST full FHIR DSTU2 compliant web service to store QuestionnaireResponse FHIR resources ([https://www.hl7.org/fhir/questionnaireresponse.html](https://www.hl7.org/fhir/questionnaireresponse.html)).

### Kibana
![Image of Kibana](https://www.elastic.co/assets/blt8a24d761d9de20f3/Screen-Shot-2015-02-17-at-3.30.30-PM-1024x715.png)

With Kibana, users can visualize their data through many different types of graphs. One can manually create visualizations in Kibana by visiting [http://localhost:5601/](http://localhost:5601/) once Elasticsearch and Kibana and installed and running, but this project can automatically generate those visualizations. 

## Installation and Deployment
This program uses the following external resources:
* Apache Maven 3.3.9: Manages the dependencies of the project. Download and install at https://maven.apache.org/download.cgi#
* Elasticsearch 2.3.4: Where the data is stored in order for it to be visualized. Download and install at https://www.elastic.co/downloads/elasticsearch
* Kibana 4.5.2: The visualization tool of Elasticsearch. Download and install at https://www.elastic.co/downloads/kibana
* OPTIONAL: Apache Tomcat 8.0.36: Web server that this project can be hosted on. Download at https://tomcat.apache.org/download-80.cgi#8.0.36

Once the above programs have been installed and the project has been either downloaded or cloned, run this command in the root of the project:
    
    mvn clean package
This command will compress the project into a war file, to be deployed on a web server of your choosing.

**If using Apache Tomcat, follow these steps**:

Move the war file into apache-tomcat-8.0.36/webapps.

In your terminal, navigate into the apache-tomcat-8.0.36 directory. For MacOS, call:
    
    bin/startup.sh
For Windows, call:
    
    bin/startup.bat

Start using the project at this URL: 'localhost:8080/visualize/fhir/QuestionnaireResponse'.

To shut down Apache Tomcat, for MacOS call:
    
    bin/shutdown.sh
For Windows, call:
    
    bin/shutdown.bat

If not using Apache Tomcat, copy the generated war file in the corresponding deployment directory.

## Running the tests
All of the testing takes place in AppTest.java, located in src/test/java. In AppTest.java, there are four main testing methods: testFileParsing, testPopulate, testVisualize, and testDelete. They are called at the bottom of the file, in the method testApp(). **IMPORTANT NOTE**: Only run one test method at a time, and leave the other methods commented. This is because Elasticsearch doesn’t update the index if you are running multiple Elasticsearch Java API calls at once, so you won’t get the results that you might expect.

## POST Requests using cURL

If you haven’t already installed cURL, run this from the command line:

	sudo apt-get install curl

**To store data in Elasticsearch:**

	curl 'yourhost/visualize/fhir/QuestionnaireResponse' --form 'file=@example_file.json' --form 'index=example_index' -- form 'type=example_type'

Parameters:

* File: The JSON file that contains the FHIR QuestionnaireResponse resource
* Index: The index in which you want to store the data in Elasticsearch
* Type: The type you want to assign the data (Elasticsearch requires "type" and "index" for organization purposes)

**To search the data in Elasticsearch**:

	curl 'yourhost/visualize/fhir/QuestionnaireResponse' --data 'command=search&index=example_index&type=example_type'

Parameters:

* Command: Specifies that you want to search
* Index: The index that you wish to search
* Type (optional): The type of data within the index that you want to receive from the search

**To export the data to a CSV file**:

	curl 'yourhost/visualize/fhir/QuestionnaireResponse' --data 'command=csv&index=example_index&type=example_type&fileName=example_csv'

Parameters:

* Command: Specifies that you want to export your data to a CSV file
* Index: The index that you wish to export the data from
* Type (optional): The type of data within the index that you wish to export
* fileName: The name of the CSV file you want to make
* **IMPORTANT NOTE**: You have to specify the destination of the exported CSV files in the config.properties file before you make this request

**To delete the data in Elasticsearch**:

	curl 'yourhost/visualize/fhir/QuestionnaireResponse' --data 'command=delete&index=example_index'

Parameters:

* Command: Specifies that you want to delete some data
* Index: The index that contains the data you want to delete

**To generate visualizations in Kibana**:

	curl 'yourhost/visualize/fhir/QuestionnaireResponse' --form 'vis_config=@example_vis_config.json' --form 'index=example_index'

Parameters:

* Vis_config: A JSON file that specifies the visualizations you want to generate
* Index: The index where the data for the visualizations is located

Example vis_config file (for an in-depth guide, go to the ** Visual Configuration Parameters and Explanation** section):

	{
		"linkId_1": {
			"graph_type" : {
				"name": "example_name_1"
			},
			"bar" : {
				"name": "bar_graph_1",
				"ranges": "0-10,10-20,20-30"
			},
			"pie" : {
				"name": "pie_chart_1"
			}
		},
		"linkId_2": {
			"line" : {
				"name": "line_graph_1",
				"aggregation": "MEDIAN",
				"interval": "auto"
			}
		}
	}

## Architecture and Design

Here is the basic structure of the project:

![Image of Architecture](https://s3.postimg.io/lub2dq3g3/Screen_Shot_2016_07_28_at_11_44_26_AM.png)

* **App.java** - Where the functionality of this project resides. Includes methods for parsing FHIR QuestionnaireResponse resources, storing data in Elasticsearch, deleting data from Elasticsearch, searching data in Elasticsearch, exporting data from Elasticsearch into a CSV file, and generating visualizations in Kibana.
* **Servlet.java** - A servlet that responds to POST requests and calls the appropriate methods from App.java based on each request.
* **AppConfig.java** - A class that accesses the properties in config.properties.
* **config.properties** - A file that contains specific variables, such as the port where Elasticsearch resides as well as the path to the destination for all CSV exports.
* **AppTest.java** - JUnit tests to ensure functionality and connectivity.

## Visual Configuration Parameters and Explanation

In order to automatically generate visualizations in Kibana based on the data stored in Elasticsearch, you have to pass a JSON file that contains the visual configuration for the visualizations you want to generate. If you are unfamiliar with JSON, read about it at [http://www.elated.com/articles/json-basics/](http://www.elated.com/articles/json-basics/).

### The Basics

In a FHIR QuestionnaireResponse resource, each question has its own unique identification, called a “linkId”. In a visual configuration file, you can create visualizations based on those linkIds, as shown:

	{
		"linkId_1": {
			"pie": {
            	"name": "pie_chart_1"            
        	}
		},
		"linkId_2": {
			"bar": {
				"name": "bar_graph_1"
			}
		}
	}

In this visual configuration file, two visualizations are being generated. For the question with the linkId "linkId_1", a pie chart is being generated (and it will be stored in Kibana with the name "pie_chart_1"). For the question with the linkId "linkId_2", a bar graph is being generated (and it will be stored in Kibana with the name "bar_graph_1").

This example visual configuration file contains all of the basics of making a visual configuration file. You have to specify the linkId of the questions you want to visualize, and then you specify how you want to visualize those questions.

Currently, this project supports three types of visualizations: pie charts, bar graphs, and line graphs. You will now be introduced to these visualizations and how to customize them to your needs.

### Pie Charts

In order to create a pie chart, you must define a new JSON object with the key "pie" within a linkId, like so: 

	{
		"linkId": {
			"pie": {
            	"name": "pie_chart_1"            
        	}
		}
	}

**Parameters**
* **"name"** (required): The name of the visualization to be made and stored in Kibana.
* **"ranges"** (optional): Creates a pie chart with different bins. For example, grouping answers by their values in the following ranges: 0 to 10, 10 to 20, 20 to 30. Proper syntax:
    

	{
		"linkId": {
			"pie": {
            	"name": "pie_chart_1",
				"ranges": "0-10,10-20,20-30"            
        	}
		}
	}
* **"splitBy"** (optional, only for longitudinal studies): Creates a pie chart that splits the data of a question by another field (usually "Patient"). Proper syntax:


	{
		"linkId": {
			"pie": {
            	"name": "pie_chart_1",
				"splitBy": "Patient"            
        	}
		},
	}

### Bar Graphs
In order to create a bar graph, you must define a new JSON object with the key “bar” within a linkId, like so:

	{
		"linkId": {
			"bar": {
            	"name": "bar_graph_1"            
        	}
		}
	}
**Parameters**
* **"name"** (required): The name of the visualization to be made and stored in Kibana.
* **"ranges"** (optional): Creates a bar chart with different bins. See an example in the **Pie Charts** section right above this.
* **"splitBy"** (optional, only for longitudinal studies): Requires the "aggregation" field. Creates a bar graph that splits the data of a question by another field (usually "Patient").
* **"aggregation"** (optional, only for longitudinal studies): Requires the "splitBy" field. Aggregates the data of the bar graph (changes the Y-axis).
    * **"AVG"**: The average of the data set.
    * **"MEDIAN"**: The median of the data set.
    * **"MIN"**: The lowest value of the data set.
    * **"MAX"**: The highest value of the data set.
    * **"SUM"**: The sum of the data set.
    * **"STD_DEV"**: The standard deviation of the data set.
    * Example:


	{
		"linkId": {
			"bar": {
            	"name": "bar_graph_1",
            	"splitBy": "Patient",
            	"aggregation": "MEDIAN"
        	}
		}
	}

### Line Graphs
Used for longitudinal studies only. In order to create a line graph, you must define a new JSON object with the key “line” within a linkId, like so:

	{
		"linkId": {
			"line": {
            	"name": "line_graph_1"            
        	}
		}
	}
**Parameters**
* **"name"** (required): The name of the visualization to be made and stored in Kibana.
* **"interval"** (required): The interval (the x-axis). Supports these intervals:
    * **"auto"**: Automatically configures the interval based on the data set.
    * **"y"**: Yearly interval.
    * **"M"**: Monthly interval.
    * **"w"**: Weekly interval.
    * **"d"**: Daily interval.
* **"splitBy"** (required): Creates a bar graph that splits the data of a question by another field (usually "Patient").
* **"aggregation"** (required): Aggregates the data of the line graph (changes the Y-axis). Supports the same aggregations that are in the **Bar Graphs** section.
* **Example**:


	{
		"linkId": {
			"line": {
            	"name": "line_graph_1",
            	"splitBy": "Patient",
            	"aggregation": "AVG",
            	"interval": "M"
        	}
		}
	}
	
### Extra Visualization Features
Make multiple visualizations for the same question:

	{
		"example_linkId": {
			"bar": {
            	"name": "example_bar_graph"
        	},
        	"pie": {
            	"name": "example_pie_chart"
        	}
		}
	}
	
Make multiple visualizations of the same type for the same question:

	{
		"example_linkId": {
			"pie": {
            	"name": "pie_chart_1"
        	},
        	"pie_2": {
            	"name": "pie_chart_2"
        	},
        	"pie_3": {
            	"name": "pie_chart_3"
        	}
		},
	}
### Longitudinal Studies
If you are looking to visualize a longitudinal study, this project will support your surveys. When storing your data in Elasticsearch, this project will automatically take note of the patient that filled out the survey, as well as taking note of the date the survey was completed. This makes it easy to create line graphs (with the x-axis as time), and it makes it easy to group data by each patient's information.
### Accessing the Visualizations
After generating visualizations using my project, you have to run both Elasticsearch and Kibana to access the visualizations. In the command line, navigate into the Elasticsearch directory. Then, call this command to start Elasticsearch:

    bin/elasticsearch
Do the same for Kibana. Navigate into the Kibana directory, and call:

    bin/kibana
Now you should be able to access [http://localhost:5601/](http://localhost:5601/), where Kibana is running. In Kibana, switch to the "Dashboard" tab. To see the visualizations, add them to the dashboard by clicking the "+" in the top-right corner of the page.
## Information About the Project
Made by Asher Dale.\
Internship at the Computational Health Informatics Program, Boston Children's Hospital.\
Developed during the summer of 2016, the summer before his senior year of high school.

## Questions
If you have any questions, feel free to reach out to me at asher@dales.org.