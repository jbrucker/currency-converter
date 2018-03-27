package fxconverter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Client for accessing the CurrencyLayer Currency Conversion web service.
 * You must supply your own API access key in a properties file named converter.properties.
 * 
 * The convert( ) method calls the web service to get one or more exchange
 * rates from the service, and return them as a JSON String.  If you call convert()
 * with no parameters, it queries the web service once for ALL exchange
 * rates and returns them all (the response data is about 3,250 bytes).
 * 
 * The parseRates(data) method parses the string returned by convert() and
 * returns a Map containing all the exchange rates.  The keys are currency codes,
 * and values are exchange rate <b>from</b> USD <b>to</b> the currency code.
 * So map.get("THB") -> 31.2 means 1 USD is 31.2 THB.
 * 
 * The parseRate(currencyCode,data) method parses the string returned by convert()
 * and returns just one exchange rate.
 * 
 * <b>Usage</b>
 * <br/>
 * The exchange rates don't change rapidly. To conserve bandwidth, converse
 * your quota of API calls, and be polite, you should only query each rate once
 * and cache the results.  The best strategy is probably to get <b>all</b> the
 * exchange rates in one API call and save them.
 * 
 * @author jim brucker
 *
 */
public class ExchangeRateService {
	// Base URL of web service, with placeholder for access key
	static final String SERVICE_URL = "http://apilayer.net/api/live?access_key=%s";
	// Debugging mode. Prints results of pattern matches.
	static final boolean DEBUG = false;
	// Perform a live query, or use saved query result read from a file?
	// Specify the filename in the main method.
	static final boolean USE_SAVED_QUERY = false;
	
	
	/**
	 * Call the exchange rate service and print the results.
	 * The USE_SAVED_QUERY flag is for development. If true,
	 * then instead of calling the service we just read data
	 * from a file.  This helps conserve our quota of API calls.
	 */
	public static void main(String[] args) throws IOException {
		
		String data;
		if (USE_SAVED_QUERY) {
			// This is a saved query response, to reduce API calls.
			String filename = "exchange-rate-2018-03-26.txt";
			System.out.println("Using saved query result in file "+filename);
			data = readFromFile(filename);
		}
		else {
			// Call the web service. Response is a JSON string containing exchange rates.
			System.out.println("Calling web service for exchange rates");
			data = queryExchangeRates();
			saveToFile(data, String.format("exchange-rate-%tF.txt", LocalDate.now()) );
		}
		
		if (DEBUG) System.out.println("Service response:");
		if (DEBUG) System.out.println(data);
		
		// Parse the response data for a single currency exchange rate
		System.out.println("Get exchange rate for a single currency:");
		System.out.println( "THB = "+parseRate("THB", data) );
		System.out.println( "JPY = "+parseRate("JPY", data) );
		
		// Get all exchange rates in the response data
		System.out.println("All exchange rates from the service:");
		Map<String,Double> rates = parseRates(data);
		printExchangeRates(rates);
	}
	
	/** Print all the exchange rates contained in a map. */
	private static void printExchangeRates(Map<String,Double> rates) {
		for(String currency: rates.keySet()) {
			System.out.printf("USD-%s = %.6f\n", currency, rates.get(currency));
		}
	}
	
	/**
	 * Get currency exchange rates from a web service.
	 * This code uses the CurrencyLayer "live" service at http://apilayer.net/live.
	 * The service returns exchange rates <b>from</b> US dollars (USD) to other currencies.
	 * It does not provide direct exchange rates between non-USD currencies (e.g. THB-EUR)
	 * but those can be imputed by dividing two rates.
	 * 
	 * @param currencyCodes currency codes (3-chars, such as JPY) you want to get rates for.
	 *    If none, then ALL exchange rates (all currencies) are retrieved.
	 *    Don't use "USD" as parameter, because that is the source currency. 
	 * @return the body of HTTP response from web service, as a String with newlines removed.
	 * @throws IOException if cannot connect to URL, or error reading from URLConnection
	 */
	public static String queryExchangeRates(String ... currencyCodes) throws IOException {
		// Query param for specifying currencies (omit this to get all currencies)
		final String CURRENCY_PARAM = "&currencies=%s";
		
		String urlstring = String.format(SERVICE_URL, Config.getApiKey() );
		// If any currencies are specified, then join them together and append
		// them to the urlstring as a query parameter.
		if (currencyCodes.length > 0) {
			urlstring += String.format(CURRENCY_PARAM, join(currencyCodes));
		}
		URL url = null;
		try {
			url = new URL(urlstring);
		} catch (MalformedURLException ex) {
			System.err.println("Invalid URL: "+urlstring);
			return "";
		}
		// openConnection returns URLConnection. Cast it to the actual type
		// so we can access special methods of HttpURLConnection.
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		// If succeeds, it will return response code HTTP_OK (200)
		int respcode = conn.getResponseCode();
		if (respcode != HttpURLConnection.HTTP_OK) {
			System.err.println("Got HTTP Response code: "+respcode);
			return "";
		}
		// Read the response into a String.
		// Java requires ridiculous amount of code for this. Python can do it in ONE line.
		BufferedReader reader = new BufferedReader( 
						new InputStreamReader( conn.getInputStream() ) );
		StringBuilder sb = new StringBuilder();
		String line = null;
		while((line=reader.readLine()) != null) sb.append(line);
		reader.close();
		return sb.toString();
	}
	
	/**
	 * Join a bunch of strings together, separated by comma.
	 * @param strings one or more strings to join. Should be at least 2.
	 * @return input parameters joined together, separated by commas.
	 */
	public static String join(String ...strings) {
		// This uses the Java 8 Streams feature.
		return java.util.Arrays.stream(strings).collect(Collectors.joining(","));
	}
	
	/**
	 * Find the exchange rate for USD to the given currencyCode,
	 * by searching the data string.
	 * 
	 * @param currencyCode the currency code to convert to, such as THB.
	 * @param data exchange rate data.  The expected format is "USDxxx":rate1,"USDyyy":rate2
	 * @return the exchange rate from USD to the given currency, or 0 if not found (or no numeric value)
	 */
	public static double parseRate(String currencyCode, String data) {
		// We are looking for a substring of the form: "USDxxx":value 
		// in the data parameter, where xxx is the currency code.
		// We want to get the value, so surround it with (...).
		// The regular expression is "USDxxx":\s*(\d*\.\d+)
		// In regular expressions, ( ) indicates a match group.
		// See the Javadoc for Pattern class or Java Tutorial on Regular Expressions.
		
		String regex = String.format("\"USD%s\":\\s*(\\d*.\\d+)", currencyCode);
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(data);
		
		if (! matcher.find() ) return 0.0;
		String value = matcher.group(1);
		if (DEBUG) System.out.printf("Found USD%s = %s\n", currencyCode, value);
		try {
			return Double.parseDouble(value);
		} 
		catch(NumberFormatException nfe) {
			return 0.0;
		}
	}
	
	/**
	 * Find and return all exchange rate values in the data String.
	 * This method parses data in the format returned by apilayer.net,
	 * which is JSON containing exchange rates <b>from</b> USD to other
	 * currencies in the format "USDxxx":rate,"USDyyy":rate2,...
	 * 
	 * @param data  the exchange rate data from apilayer.net.   
	 * @return map of exchange rates from USD to other currencies. 
	 *    The map keys are the currency codes, and values are the 
	 *    exchange rates. Keys are sorted alphabetically.
	 */
	public static Map<String,Double> parseRates(String data) {
		// We are looking for substrings of the form: "USDxxx":value 
		// in the data parameter, where xxx is the currency code.
		// We want to get both the currency code and the exchange rate, 
		// so surround those parts with ( ).
		// The regular expression is "USD([A-Z]{3})":\s*(\d*\.\d+)
		// In regular expressions, ( ) indicates a match group.
		// See the Javadoc for Pattern class or Java Tutorial on Regular Expressions.
		
		String regex = "\"USD([A-Z]{3})\":\\s*(\\d*.\\d+)";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(data);
		Map<String,Double> rates = new TreeMap<>();
		
		int offset = 0;
		while( matcher.find(offset) ) {
			String code = matcher.group(1);
			String value = matcher.group(2);
			if (DEBUG) System.out.printf("Found USD%s = %s\n", code, value);
			try {
				double rate = Double.parseDouble(value);
				rates.put(code, rate);
			}
			catch (NumberFormatException nfe) {
				System.err.printf("Invalid number in exchange rate: USD%s=%s\n", code,value);
			}
			// move starting point for next match
			offset = matcher.end();
		}
		return rates;
	}

	/**
	 * Save data string to a file.
	 * @param data the string to write or append to file.
	 * @param filename the filename to write to.
	 */
	private static void saveToFile(String data, String filename) {
		final boolean append = false; // append to file or overwrite?
		try {
			FileWriter writer = new FileWriter(filename, append);
			writer.append(data);
			writer.close();
		} catch (IOException e) {
			System.err.println("Could not write data to file "+filename);
		}
	}

	/**
	 * Read data from a file and return it as a String.
	 * @param filename is the filename to read 
	 * @return String containing all data, including newlines. Empty string if no file.
	 */
	private static String readFromFile(String filename) {
		StringBuilder sb = new StringBuilder();
		try {
			FileReader reader = new FileReader(filename);
			BufferedReader breader = new BufferedReader(reader);
			String line = null;
			while((line=breader.readLine()) != null) sb.append(line).append('\n');
			reader.close();
		} catch (IOException e) {
			System.err.println("Could not read file "+filename);
		}
		return sb.toString();
	}
}
