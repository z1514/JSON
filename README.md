JSON in Java [package org.json]
===============================

[![Maven Central](https://img.shields.io/maven-central/v/org.json/json.svg)](https://mvnrepository.com/artifact/org.json/json)


# Overview

[JSON](http://www.JSON.org/) is a light-weight language-independent data interchange format.

This project is intended for learning and improving the org.json library's functions. Based on the original org.json package, this version includes more useful functions like reading and replacing a child JSON object quickly, dealing with JSON objects in concurrent style, and so on. 


# Build Instructions

The org.json package can be built from the command line, Maven, and Gradle. The unit tests can be executed from Maven, Gradle, or individually in an IDE e.g. IntelliJ IDEA.

**Building and testing in IDEA IDE(recommend)**

Just clone the project into a local device and import this project as a Maven project into IDEA IDE. Now the user can read and run the XML class and XMLTest class directly to do the tests (for this project, since there will be a difference between devices, using IDE is more stable). 
 
**Building from the command line**

*Build the class files from the package root directory src/main/java*
````
javac org/json/*.java
````

*Create the jar file in the current directory*
````
jar cf json-java.jar org/json/*.class
````

*Compile a program that uses the jar (see example code below)*
````
javac -cp .;json-java.jar Test.java (Windows)
javac -cp .:json-java.jar Test.java (Unix Systems)
````

*Test file contents*

````
import org.json.JSONObject;
public class Test {
    public static void main(String args[]){
       JSONObject jo = new JSONObject("{ \"abc\" : \"def\" }");
       System.out.println(jo.toString());
    }
}
````

*Execute the Test file*
```` 
java -cp .;json-java.jar Test (Windows)
java -cp .:json-java.jar Test (Unix Systems)
````

*Expected output*

````
{"abc":"def"}
````

 
**Tools to build the package and execute the unit tests**

Execute the test suite with Maven:
```
mvn clean test
```

Execute the test suite with Gradlew:

```
gradlew clean build test
```

# Notes

  ## Millstone 2

**Implemented Methods**
```
 public static JSONObject toJSONObject(Reader reader, JSONPointer path)
 ```
 
  This function is to read the sub-object directly in the key path. It reuses and modifies some code in the parse method. 
 
 ```
 public static JSONObject toJSONObject(Reader reader, JSONPointer path, JSONObject replacement)
 ```
 
  This function is to replace the JSON objects on the keypath with a specified JSON object. It reuses and modifies some code in the parse method. In addition, to make this function work, an overloaded parse function is implemented and used in this function. 
  
 ```
 private static boolean parse(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config, String[] tokens, int position, JSONObject replace)
```
 
 This overloaded parse function is used to help find and replace JSON objects in the keypath. It utilizes and modified codes in original parse function to achieve the features. 

**Unit Test**

 There are six unit test methods for millstone 2. These test cases cover the right and wrong conditions. The details are listed in the XMLTest class. 
 
 ```
  public void testToJSONWithPathWhenPathEmpty()
  public void testToJSONWithPathWhenPathExists()
  public void testToJSONWithPathWhenPathNotExists()
  public void testToJSONWithReplaceWhenPathEmpty()
  public void testToJSONWithReplaceWhenPathExists()
  public void testToJSONWithReplaceWhenPathNotExists()
 ```
 
 **Assumptions**
 
  These two methods can only be used on JSONObject. They can't be applied to an XML file with JSONArray. More constraint can be found in the function comments in the XML and XMLTest file. 
 
 **Summary**
 
  Correctness: For the right test cases, unit tests pass rightly. For wrong conditions, the tests will throw JSONException and indicate the cause of the problems. The methods can finish their tasks rightly.
   
  Efficiency: 
  
  For the first task, the function reused the code of the parse function. It will skip unrelated sections quickly until finding the target part, or just throw an exception when such a path doesn't exist. Since it doesn't read all the XML, it is much faster than the original functions. 
  
  The second task overloaded the parse function in a recursive style. When it reaches the final key path, it will skip the JSON object in the original XML file and do the replacement directly. The implementation in the library is faster than the original method since it does the replacement in the process of converting the XML into JSON. The function will not need the whole json object to do the replacement. A shortcoming is that the efficiency boost relies on the sub JSON objects' size. 
  
  ## Millstone 3

**Implemented Methods**
```
 public static JSONObject toJSONObject(Reader reader, Function<String,String> keyTransformer)
 ```
 
  This method uses reader to read text in xml, and apply key transformer on the keys in xml files.
  
 ```
 private static boolean parse(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config, Function<String,String> keyTransformer)
```
 
 This overloaded parse function reuses original parse method codes, and uses Function keyTransformer on tagName when it add jsonObject under a tagName. 

**Unit Test**

 There are one unit test methods for millstone 3. These test cases cover cases like adding letters, reversing order and changing words to uppercases. The details are listed in the XMLTest class. 
 
 ```
  public void testToJSONWithKeyTransformer()
 ```
 
 **Assumptions**
 
  This method can work on both json objects and json arrays. But when it deals with large files (>1GB), it's possible to fail. 
 
 **Summary**
 
  Correctness: For all kinds of cases, unit tests pass rightly. The methods can finish their tasks rightly.
   
  Efficiency: The new method is much faster than client method in millstone 1. Based on tests results, the new method can save 50-60% time than client method before. This is because the new method transform the keys during parsing. In the process, the method doesn't need to generate a whole json object at first and then iterate the whole object again. Since it just reads the xml text in one time and doesn't deal with json object one more time, the test results are reasonable. 
  
  I tested 10 files. The sample.xml file in this project is from https://docs.microsoft.com/en-us/previous-versions/windows/desktop/ms762271(v=vs.85), other xml files are from wikimedia (from 20MB to 1GB).
  
  ## Millstone 4

**Implemented Methods and Classes**
```
 public Stream<Entry<String,Object>> toStream()
 ```
 
  This method is used to transform the JSONObject into a key-value stream. 
  
 ```
 public Spliterator<Map.Entry<String,Object>> spliterator()
```
 
 To make toStream method work, a spliterator method is implemented. It is used in StreamSupport.stream method to generate the stream.
 
 ```
 static class JSONObjectSpliterator implements Spliterator<Map.Entry<String,Object>>
 ```
 
 To make the stream work, how to splite the JSONObject into stream needs to be specified.  JSONObjectSpliterator class implements tryAdvance method to analyze the tree structure of the JSONObject, so spliterator() method can return a spliterator of JSONObject for StreamSupport.stream method.
 
 

**Unit Test**

 There are three unit test methods for millstone 4. These test cases cover cases like using forEach, map and filter methods on the stream to show whether the generated stream can work correctly.
 
 ```
  public void jsonObjectStreamForEachTest()
  public void jsonObjectStreamMapTest()
  public void jsonObjectStreamFilterTest()
 ```
 
 An example of output is below:
 
 ```
Books={"book":[{"author":"ASmith","title":"AAA"},{"author":"BSmith","title":"BBB"}],"id":1,"value":"File"}
book=[{"author":"ASmith","title":"AAA"},{"author":"BSmith","title":"BBB"}]
0={"author":"ASmith","title":"AAA"}
author=ASmith
title=AAA
1={"author":"BSmith","title":"BBB"}
author=BSmith
title=BBB
id=1
value=File
 ```
 
 **Summary**
 
  Correctness: For all kinds of cases, unit tests pass correctly and the output results are right. The toStream method works.
   
  For this millstone, I generate a stream of key-values rather than JSONObject. This is because there are several instance types in JSONObject like JSONObject, JSONArray, and Primitives. To keep the tree structure and not add new nodes in our stream(transform json array to a json object), using key-value is a good choice. Besides, for each object in a JSONArray, I add an ordinal key for them. In this way, it will be convenient to figure out a json array in the output. 
  
   ## Millstone 5

**Implemented Methods and Classes**
```
 public static Future<JSONObject> toJSONObject(Reader reader, Function<JSONObject,Void> onFinish, Function<Exception,Void> onError)
 ```
 
  This method is used to transform the JSONObject asynchronously by using Future, Thread pool and existing methods. 
  
**Unit Test**

 There are two unit test methods for millstone 5. These test cases use right XML text and wrong XML text as input to test whether the XML will be transformed into JSON object and invoke the corresponding Function when the transformation successes and fails.
 
 ```
  public void testToJSONConcurrentSuccess()
  public void testToJSONConcurrentError()
 ```

 **Summary**
 
  Correctness: For all kinds of test cases, unit tests pass correctly and the output matches the expected. The toJSONObject method works.
   
  To make the method work asynchronously, I used Future and Thread pool in the XML class. Besides, I add two Function objects as arguments to figure out what to do on success and failure. In this way, the users can define their logic for the callback functions. This is quite similar to the JavaScript CallBack Style and this method just works.
