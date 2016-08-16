/*
 * Asher Dale
 * Summer 2016
 * Internship at BCH-CHIP
 */

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


 // Class that manages the configuration parameters
public class AppConfig {

    public static final String CONFIG_PROPERTIES_FILE = "config.properties";

    public static final String PORT = "port";
    public static final String SERVER = "server";
    public static final String PATH_TO_CSV_EXPORTS = "path_to_csv_exports";
    public static final String PATH_TO_TEST_DATA = "path_to_test_data";
    public static final String PATH_TO_TEST_VIS_CONFIG = "path_to_test_vis_config";

    private static Properties prop = new Properties();
    
    // Upload the configuration from config.properties files
    private static void uploadConfiguration() throws IOException {
        InputStream input = null;

        String filename = CONFIG_PROPERTIES_FILE;
        input = AppConfig.class.getResourceAsStream("/"+filename);
        
        if (input == null) {
         	System.out.println("null");
        }
        if (input != null) {
        	prop.load(input);
        	input.close();
        }
    }

    /**
     * Get the value of the property described in conf.properties file
     * @param key The key of the property
     * @return The value of the property.
     */
    public static String getProp(String key) throws IOException {
        if (prop.isEmpty()) {
            uploadConfiguration();
        }
        return prop.getProperty(key);
    }
}