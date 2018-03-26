package fxconverter;

import java.util.ResourceBundle;

/**
 * Configuration info for the application, using
 * a resource bundle named "converter.properties".
 * Get the API Key.
 */
public class Config {
	public static final String PROPERTY_BUNDLE = "converter";
	
	private static String apiKey = null;
	
	public static String getApiKey() {
		if (apiKey == null) loadProperties();
		return apiKey;
	}
	
	private static void loadProperties() {
		try {
			ResourceBundle bundle = ResourceBundle.getBundle(PROPERTY_BUNDLE);
			apiKey = bundle.getString("api.key");
		}
		catch( java.util.MissingResourceException ex) {
			System.out.printf("Please create a properties file named: %s.properties\n", PROPERTY_BUNDLE);
			System.out.println("Containing your API Key in the format:");
			System.out.println("api.key = 1234567890ABCDEF");
			System.out.println("Add the file to the application classpath, e.g. in src/ directory.");
			System.exit(1);
		}
	}
	
	public static void main(String[] args) {
		System.out.println("Your api key is "+getApiKey());
	}
}
