## Using a Web Service for Currency Exchange Rates

This code shows how to get currency exchange rates using the web service at `http://apilayer.net/live`.  Three pieces of Java you need are explained below:

1. How to send an HTTP request and read the reply using URL and HttpURLConnection.
2. How to parse exchange rates from the reply using a *regular expression*.
3. How to use a Map to store the exchange rates, with currency code as the map key.

## Using a Web Service

To use any web service you need to know the URL for the service,
any parameters that the service requires, and what information the
service URL will return.  This is the web service's API.

For the [CurrencyLayer.com](https://currencylayer.com) exchange rate service the API Documentation and examples are at [Documentation](https://currencylayer.com/documentation).

The free "live" quote service has:

* **Service URL**: `http://apilayer.net/api/live?query_params`
* **query params**:
   * `access_key=xxxxxxxxxxxxxxxxx` your access key (required)
   * `currencies=CODE1,CODE2...` optional currency codes. If omitted the service returns all exchange rates from USD.
   * `source=CUR` (paid account only) the source currency code for conversion rates. Default is "USD".
 * **Response:** a string containing exchange rates, in JSON format (examples below).

To use this service you need to get an API access key by registering at [https://currencylayer.com/product](https://currencylayer.com/product). Select the free account.

## Sample Request and Reply

To check our access key and see what the service reply looks like, send
a sample query using a web browser (with your real access_key, of course):
```
http://apilayer.net/api/live?access_key=1234567890abcdef
```
The reply should look like (but all on one line):
```
{"success":true,"terms":"https://currencylayer.com/terms",
 "privacy":"https://currencylayer.com/privacy",
 "timestamp":1521947957,
 "source":"USD",
 "quotes":{"USDAED":3.672504,"USDAFN":68.930404,"USDALL":104.800003,
           "USDAMD":479.670013,"USDANG":1.780403,"USDAOA":214.358994,
           ... (more exchange rates)
           "USDZMK":9001.203593,"USDZMW":9.430363,"USDZWL":322.355011}
}
```

The format is JavaScript Object Notation (JSON), which is a collection of key-value pairs.  The keys are always Strings and surrounded by "quotes". The values can be anything, including a nested JSON object delimited by {...}.

For more complex web services a browser plugin like "REST Console" for Chrome is useful for exploring the service.

## Java URL and HttpURLConnection

This section describes how to use Java to send an HTTP request
and process the response.

To send an Http request in Java first create a URL:
```java
import java.net.URL;

...
    final String MY_API_KEY = "0123456789ABCDEF";
    URL url = new URL("http://apilayer.net/api/live?access_key="+ MY_API_KEY );
```
This may throw MalformedURLException, so surround the code with try-catch.

Once you have a URL, open a connection to it. This sends the request to the remote server and gets the response.  
```java
    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
```
The `url.openConnection()` method returns a `URLConnection` object.  For HTTP/HTTPS requests, the actual object is `HttpURLConnection`. 
The HttpURLConnection has extra methods such as getting the HTTP response code, so use a cast.

If the request succeeds, it will return Response Code 200 (OK). The
HttpURLConnection class has named constants for all the HTTP response
codes, so we can write:
```java
    int respcode = conn.getResponseCode();
    if (respcode != HttpURLConnection.HTTP_OK) {
        System.err.println("Got HTTP Response code: "+respcode);
        return;
    }
```

After checking the response code, open an InputStream to read the data in the response body, which contains the info we want.
```java
   InputStream in = conn.getInputStream();
```
The response from this service is not too long (at most 3,300 bytes) so we can read everything into a String and close the InputStream:
```java
    InputStream in = conn.getInputStream();
    BufferedReader reader = new BufferedReader( new InputStreamReader( in ) );
    StringBuilder sb = new StringBuilder();
    String line = null;
    while((line=reader.readLine()) != null) sb.append(line);
    reader.close();
    // The response data as a String
    String data = sb.toString();
```
Closing the BufferedReader (or Scanner) also closes the underlying InputStream.  Its good to close input streams in order to free resources.

## Parsing the Data

The service response is in JSON (JavaScript Object Notation) format, a standard and widely used data format. 

For this application the data format is quite simple, so we can parse it
ourselves using the *regular expression* classes included in the JDK.
The exchange rate data we want always has this format:
```
    "USDTHB":31.17037,"USDJPY":104.728996,"USDEUR":0.807902,...
```
*Regular Expressions* are a common syntax for searching data using a pattern.  They are supported by almost all programming languages, text editors, and some Linux shell commands.  Links to regular expression tutorials are given in the References below.  

| String to Match     | Regular Expression     | Meaning             |
|:--------------------|:-----------------------|:--------------------|
| USDTHB              | USD[A-Z]{3}            | Match USD followed by any 3 letters. |
| 31.17037            | \d+\\.\d+               | Match one or more digits (\d), a period, and more digits |
| "USDTHB":31.17037   | "USD[A-Z]{3}":\d+\\.\d+ | Combine above 2 patterns. |
| "USDJPY":  104.7289 | "USD[A-Z]{3}":\s*\d+\\.\d+ | Allow spaces (\s*) after the colon |
| add match groups   | "USD([A-Z]{3})":\s*(\d+\\.\d+) | Save whatever matches inside () as a *match group* |

The meaning of the expressions are:
* `USD` - match the string "USD" (literal match)
* `[A-Z]` - match any letter A to Z
* `[A-Z]{3}` - match any 3 letters A to Z, such as THB
* `\s*` - match zero or more whitespace characters (\s=whitespace)
* `\d`  - match any digit. Same as writing `[0-9]`.
* `\d+` - match one or more digits.
* `([A-Z]{3})` - match 3 letters A-Z and save the result in a *match group* that can be retrieved later. `(..)` defines a match group inside a pattern.

Suppose the data we want to search is in a String variable named `data`.  To create a regex to match exchange rates use:
```java
String regex = "\"USD([A-Z]{3})\":\\s*(\\d*.\\d+)";
Pattern pattern = Pattern.compile(regex);
Matcher matcher = pattern.matcher(data);
```
The `Matcher` object matches a Pattern to a String (data).
`Matcher` has 3 methods to perform matching: `matches()`, `find()`, and `find(int offset)`.  

We want to find all exchange rates in the data by searching for the pattern in the data many times. So use `matcher.find(offset)`:

```java
int offset = 0;
while( matcher.find(offset) ) {
    String currency = matcher.group(1);
    String value = matcher.group(2);
    System.out.printf("Found USD->%s = %s\n", currency, value);
    // move offset to the end of the previous match
    offset = matcher.end();
}
```
After the Matcher finds a pattern, we call `matcher.group(n)` to get a "match group" from the text just matched. Use `.group(1)` for the first match group (the currency code).  `matcher.group(0)` returns *everything* that matched the pattern.

## Save Exchange Rates using a Map

We want to save the exchange rates so we don't need to repeatedly query the web service.

A `Map` is a mapping of keys to values. In Java, a Map has two type parameters: `Map<Key,Value>`, where the type parameters specify the actual class of the keys and values.  For exchange rates the keys are `String` and values are `Double`.
```java
Map<String,Double> rates = new TreeMap<>();
// add exchange rates to the map
rates.put("THB", 31.17037);
rates.put("JPY", 104.728996);

// get the value for the key "THB"
double value = rates.get("THB");
// get a value and specify a default value in case the key is not in the map
double value2 = rates.getOrDefault("EUR", 0.0);
```

You can add a Map to the example pattern matching code (above) to
save all the currency exchange rates in a Map, instead of printing them.
If you call `map.put(key,value)` for a key that is already in the map,
the new value replaces the old one.

The JDK has several Map classes including HashMap and TreeMap.  A TreeMap always returns its keys in sorted order, which is useful if you want to get the keys and display them:
```java
// Get all the keys in the map
Set<String> keys = rates.keySet();
System.out.println("Currencies are:");
for (String currency: keys) System.out.println(currency);
```

## Assemble the Pieces

The above examples show the pieces you need to use the exchange rate service.  By getting all the exchange rates at once and saving them in a Map, you can conserve you quota of API queries.  

Even if you *don't* query all the exchange rates at once, you should *still* use a Map to remember the currencies you have already queried and avoid redundantly querying for the same exchange rate.

The `ExchangeRateService` class demonstrates the pieces used together.

## Separate Your Code (the *Single Responsibility Principle*)

You want your application to be flexible, easy to test, and easy to modify.  So, you should isolate your Exchange Rate code in a class by itself, and design methods to provide the info your application needs.

For example:

| ExchangeRateService |
|---------------------|
| getCurrencyCodes():  String[ ] <br/> getExchangeRate(fromCurr, toCurr):  double <br/> getExchangeRates():  Map |


Your code will be cleaner, easier to develop and test, and you can easily switch to a different exchange rate service or use the Exchange Rate service class in a different application.

## Running The Sample Code

Sample code to download and print all exchange rates is in the class `ExchangeRateService`.

Before you can run it, you need to put your CurrencyLayer.com API Key in a
properties file named `converter.properties` on the Java classpath.  Create a properties file `src/converter.properties` containing your API Key:
```
# Your API Key.
# Avoid committing this to a public repository.
api.key = 1234567890ABCDEF
```
Verify it by running `Config.java` which will print the API key on the console.
Then run the `ExchangeRateService` class.

## Protect Your API Keys

Your API key or other credentials should be kept secret.  So, don't put your keys into version control (git) unless its a private in-house VCS or a private repo from a trusted provider (Github and Bitbucket are OK).

If your API Key (or credentials) are in a properties file, you can avoid
accidentally committing it to Github by: (a) add the properties filename to .gitignore,
(b) put the properties file in a directory **outside** your project directory
and add the directory to your application classpath.

The same advice applies to JAR files.  If you build a JAR for your application and
your API Key is in the JAR file, someone can easily extract it from the JAR file.

## References

* [CurrencyLayer.com Documentation](https://currencylayer.com/documentation) how to use their web service.
* [Reading and Writing a URLConnection](https://docs.oracle.com/javase/tutorial/networking/urls/readingWriting.html) - Oracle Java Tutorial.
* [Java HttpURLConnection Example](https://www.journaldev.com/7148/java-httpurlconnection-example-java-http-request-get-post) at JournalDev.com shows how to get the HTTP Response Code, set HTTP headers, and more.
* Another [Java HttpURLConnection Example](https://alvinalexander.com/blog/post/java/how-open-url-read-contents-httpurl-connection-java) with more detail and explanation.  The author has written several books about programming.
* Parsing JSON. In this code we used regex. You can also convert JSON to/from Java using the popular [GSON](https://github.com/google/gson/) or [Jackson](https://github.com/FasterXML/jackson) libraries.
* Regular Expression Tutorials:
[Vogella](http://www.vogella.com/tutorials/JavaRegularExpressions/article.html), [BeginnersBook](https://beginnersbook.com/2014/08/java-regex-tutorial/), and [JavaTPoint](https://www.javatpoint.com/java-regex).
* [Regular Expression Tool](https://regexr.com) at [https://regexr.com](https://regexr.com) learn, explore, and test regex -- visually shows what's going on. Many examples, too.
* One SKE graduate told me that the most useful thing he learned from OOP was *regular expressions*.
