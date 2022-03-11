package org.json;

/*
Copyright (c) 2015 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;


/**
 * This provides static methods to convert an XML text into a JSONObject, and to
 * covert a JSONObject into an XML text.
 *
 * @author JSON.org
 * @version 2016-08-10
 */
@SuppressWarnings("boxing")
public class XML {

    /** The Character '&amp;'. */
    public static final Character AMP = '&';

    /** The Character '''. */
    public static final Character APOS = '\'';

    /** The Character '!'. */
    public static final Character BANG = '!';

    /** The Character '='. */
    public static final Character EQ = '=';

    /** The Character <pre>{@code '>'. }</pre>*/
    public static final Character GT = '>';

    /** The Character '&lt;'. */
    public static final Character LT = '<';

    /** The Character '?'. */
    public static final Character QUEST = '?';

    /** The Character '"'. */
    public static final Character QUOT = '"';

    /** The Character '/'. */
    public static final Character SLASH = '/';

    /**
     * Null attribute name
     */
    public static final String NULL_ATTR = "xsi:nil";

    public static final String TYPE_ATTR = "xsi:type";

    /**
     * Thread pool for asynchronous toJSONObject method
     */
    private static ExecutorService executor = Executors.newFixedThreadPool(4);;

    /**
     * Creates an iterator for navigating Code Points in a string instead of
     * characters. Once Java7 support is dropped, this can be replaced with
     * <code>
     * string.codePoints()
     * </code>
     * which is available in Java8 and above.
     *
     * @see <a href=
     *      "http://stackoverflow.com/a/21791059/6030888">http://stackoverflow.com/a/21791059/6030888</a>
     */
    private static Iterable<Integer> codePointIterator(final String string) {
        return new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    private int nextIndex = 0;
                    private int length = string.length();

                    @Override
                    public boolean hasNext() {
                        return this.nextIndex < this.length;
                    }

                    @Override
                    public Integer next() {
                        int result = string.codePointAt(this.nextIndex);
                        this.nextIndex += Character.charCount(result);
                        return result;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    /**
     * Replace special characters with XML escapes:
     *
     * <pre>{@code 
     * &amp; (ampersand) is replaced by &amp;amp;
     * &lt; (less than) is replaced by &amp;lt;
     * &gt; (greater than) is replaced by &amp;gt;
     * &quot; (double quote) is replaced by &amp;quot;
     * &apos; (single quote / apostrophe) is replaced by &amp;apos;
     * }</pre>
     *
     * @param string
     *            The string to be escaped.
     * @return The escaped string.
     */
    public static String escape(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (final int cp : codePointIterator(string)) {
            switch (cp) {
            case '&':
                sb.append("&amp;");
                break;
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '"':
                sb.append("&quot;");
                break;
            case '\'':
                sb.append("&apos;");
                break;
            default:
                if (mustEscape(cp)) {
                    sb.append("&#x");
                    sb.append(Integer.toHexString(cp));
                    sb.append(';');
                } else {
                    sb.appendCodePoint(cp);
                }
            }
        }
        return sb.toString();
    }

    /**
     * @param cp code point to test
     * @return true if the code point is not valid for an XML
     */
    private static boolean mustEscape(int cp) {
        /* Valid range from https://www.w3.org/TR/REC-xml/#charsets
         *
         * #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
         *
         * any Unicode character, excluding the surrogate blocks, FFFE, and FFFF.
         */
        // isISOControl is true when (cp >= 0 && cp <= 0x1F) || (cp >= 0x7F && cp <= 0x9F)
        // all ISO control characters are out of range except tabs and new lines
        return (Character.isISOControl(cp)
                && cp != 0x9
                && cp != 0xA
                && cp != 0xD
            ) || !(
                // valid the range of acceptable characters that aren't control
                (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF)
            )
        ;
    }

    /**
     * Removes XML escapes from the string.
     *
     * @param string
     *            string to remove escapes from
     * @return string with converted entities
     */
    public static String unescape(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (int i = 0, length = string.length(); i < length; i++) {
            char c = string.charAt(i);
            if (c == '&') {
                final int semic = string.indexOf(';', i);
                if (semic > i) {
                    final String entity = string.substring(i + 1, semic);
                    sb.append(XMLTokener.unescapeEntity(entity));
                    // skip past the entity we just parsed.
                    i += entity.length() + 1;
                } else {
                    // this shouldn't happen in most cases since the parser
                    // errors on unclosed entries.
                    sb.append(c);
                }
            } else {
                // not part of an entity
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Throw an exception if the string contains whitespace. Whitespace is not
     * allowed in tagNames and attributes.
     *
     * @param string
     *            A string.
     * @throws JSONException Thrown if the string contains whitespace or is empty.
     */
    public static void noSpace(String string) throws JSONException {
        int i, length = string.length();
        if (length == 0) {
            throw new JSONException("Empty string.");
        }
        for (i = 0; i < length; i += 1) {
            if (Character.isWhitespace(string.charAt(i))) {
                throw new JSONException("'" + string
                        + "' contains a space character.");
            }
        }
    }

    /**
     * Scan the content following the named tag, attaching it to the context.
     *
     * @param x
     *            The XMLTokener containing the source string.
     * @param context
     *            The JSONObject that will include the new material.
     * @param name
     *            The tag name.
     * @return true if the close tag is processed.
     * @throws JSONException
     */
    private static boolean parse(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config)
            throws JSONException {
        char c;
        int i;
        JSONObject jsonObject = null;
        String string;
        String tagName;
        Object token;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter; //change interface

        // Test for and skip past these forms:
        // <!-- ... -->
        // <! ... >
        // <![ ... ]]>
        // <? ... ?>
        // Report errors for these forms:
        // <>
        // <=
        // <<

        token = x.nextToken();
//        System.out.println(token);
        // <!

        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;//pass
                }
                x.back();
            } else if (c == '[') {
                token = x.nextToken();//token inside the xml symbols
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            context.accumulate(config.getcDataTagName(), string);
                        }
                        return false;//cdata
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();//pass the section
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");//Returns the next XML meta token. This is used for skipping over <!...>and <?...?> structures.
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        } else if (token == QUEST) {

            // <?
            x.skipPast("?>");
            return false;
        } else if (token == SLASH) {

            // Close tag </

            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);//the name and the tag is different
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;//close the token

        } else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");//impossible

            // Open tag <

        } else {
//            System.out.println(context);
            tagName = (String) token;//read the name
//            System.out.println(tagName); iterate the tags in recursive way
            token = null;
            jsonObject = new JSONObject();//a new Object
            boolean nilAttributeFound = false;//null attribute?
            xmlXsiTypeConverter = null;//type changer?
            for (;;) {
                if (token == null) {
                    token = x.nextToken();
//                    System.out.println(token); //read >
                }
                // attribute = value
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();//attribute
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");//if no value?
                        }
                        if (config.isConvertNilAttributeToNull()//based on the xml, the rules are complex
                                && NULL_ATTR.equals(string)
                                && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;//a null attribute
                        } else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                                && TYPE_ATTR.equals(string)) {//type transformation
                            xmlXsiTypeConverter = config.getXsiTypeMap().get(token);//get the type converter
                        } else if (!nilAttributeFound) {
                            //System.out.println(jsonObject);
                            jsonObject.accumulate(string,
                                    config.isKeepStrings()
                                            ? ((String) token)
                                            : stringToValue((String) token));//change to value
                           // System.out.println(jsonObject);
                        }
                        token = null;
                    } else {//empty
                        jsonObject.accumulate(string, "");
                    }


                } else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (config.getForceList().contains(tagName)) {//???
                        // Force the value to be an array
                        if (nilAttributeFound) {
                            context.append(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(tagName, jsonObject);
                        } else {
                            context.put(tagName, new JSONArray());//at first, there is no length
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(tagName, jsonObject);
                        } else {
                            context.accumulate(tagName, "");//under a key
                        }
                    }
                    return false;

                } else if (token == GT) {//normal
                    // Content, between <...> and </...>
                    for (;;) {
                        token = x.nextContent();
//                        System.out.println(token); iterate all the parts of the current elements read string or <
                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;//with out content
                        } else if (token instanceof String) {
//                            System.out.println(token); when this is a string
                            string = (String) token;
                            if (string.length() > 0) {
                                if(xmlXsiTypeConverter != null) {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                            stringToValue(string, xmlXsiTypeConverter));//give a name
                                } else {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                            config.isKeepStrings() ? string : stringToValue(string));
//                                    System.out.println(config.getcDataTagName());
//                                    System.out.println(jsonObject);
                                }
                            }

                        } else if (token == LT) {
                            // Nested element
//                            System.out.println(token); nested elements
//                            System.out.println(jsonObject); //DFS strange output
                            if (parse(x, jsonObject, tagName, config)) {
                                if (config.getForceList().contains(tagName)) {
                                    // Force the value to be an array
//                                    System.out.println(tagName);
                                    if (jsonObject.length() == 0) {
                                        context.put(tagName, new JSONArray());
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {//change
                                        context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.append(tagName, jsonObject);
                                    }
                                } else {
                                    //System.out.println(jsonObject);
                                    if (jsonObject.length() == 0) {
                                        context.accumulate(tagName, "");
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {
//                                        System.out.println("before:"+jsonObject);
//                                        System.out.println(config.getcDataTagName());
//                                        System.out.println(tagName);
                                        context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
//                                        System.out.println("after:"+context);
                                    } else {
//                                        System.out.println(context);
//                                        System.out.println(tagName);
//                                        System.out.println(jsonObject);
                                        context.accumulate(tagName, jsonObject);
//                                        System.out.println(context);
                                    }
                                }
                                
                                return false;
                            }
                        }
                    }
                } else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }

    /**
     * This method is used to replace jsonObject on specified key path. This parse functions
     * reuse most of the codes of original parse function. In addition, this function take the key path
     * into consideration and replace the objects in the parsing process. In this way, there is no need to
     * generate the whole json object first. The function will just skip the sub object in the key path and
     * do replacement directly, which will enhance the efficiency.
     *
     * @param x The xml Tokener for the source reader
     * @param context JSONObject, in the recursive process, it will gradually accumulate to the whole object
     * @param name tag name of the parent element
     * @param config parse config
     * @param tokens the tokens in the path
     * @param position current position of token in the tokens, used to tell whether the function reach the point to do replacement
     * @param replace The object used to replace
    */
    private static boolean parse(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config, String[] tokens, int position, JSONObject replace)
            throws JSONException{
        /*
        The code below is just the same as the original parse
         */
        char c;
        int i;
        JSONObject jsonObject = null;
        String string;
        String tagName;
        Object token;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter; //change interface

        // Test for and skip past these forms:
        // <!-- ... -->
        // <! ... >
        // <![ ... ]]>
        // <? ... ?>
        // Report errors for these forms:
        // <>
        // <=
        // <<

        token = x.nextToken();
//        System.out.println(token);
        // <!

        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;//pass
                }
                x.back();
            } else if (c == '[') {
                token = x.nextToken();//token inside the xml symbols
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            context.accumulate(config.getcDataTagName(), string);
                        }
                        return false;//cdata
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();//pass the section
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");//Returns the next XML meta token. This is used for skipping over <!...>and <?...?> structures.
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        } else if (token == QUEST) {

            // <?
            x.skipPast("?>");
            return false;
        } else if (token == SLASH) {

            // Close tag </

            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);//the name and the tag is different
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;//close the token

        } else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");//impossible

            // Open tag <

        }
        /*
        The codes below are modified to do the replacement
         */
        else if (!((String)token).equals(tokens[position]))//when the current token is different with the token in current position in tokens
        {
            //just do the same thing as the original parse function, because there is no need to do the replacement
            //the code below is the same as the last part in the original parse function
            tagName = (String) token;//read the name
            token = null;
            jsonObject = new JSONObject();//a new Object
            boolean nilAttributeFound = false;//null attribute?
            xmlXsiTypeConverter = null;//type changer?
            for (;;) {
                if (token == null) {
                    token = x.nextToken();
//                    System.out.println(token); //read >
                }
                // attribute = value
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();//attribute
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");//if no value?
                        }
                        if (config.isConvertNilAttributeToNull()//based on the xml, the rules are complex
                                && NULL_ATTR.equals(string)
                                && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;//a null attribute
                        } else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                                && TYPE_ATTR.equals(string)) {//type transformation
                            xmlXsiTypeConverter = config.getXsiTypeMap().get(token);//get the type converter
                        } else if (!nilAttributeFound) {
                            //System.out.println(jsonObject);
                            jsonObject.accumulate(string,
                                    config.isKeepStrings()
                                            ? ((String) token)
                                            : stringToValue((String) token));//change to value
                            // System.out.println(jsonObject);
                        }
                        token = null;
                    } else {//empty
                        jsonObject.accumulate(string, "");
                    }


                } else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (config.getForceList().contains(tagName)) {//???
                        // Force the value to be an array
                        if (nilAttributeFound) {
                            context.append(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(tagName, jsonObject);
                        } else {
                            context.put(tagName, new JSONArray());//at first, there is no length
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(tagName, jsonObject);
                        } else {
                            context.accumulate(tagName, "");//under a key
                        }
                    }
                    return false;

                } else if (token == GT) {//normal
                    // Content, between <...> and </...>
                    for (;;) {
                        token = x.nextContent();
                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;//with out content
                        } else if (token instanceof String) {
                            string = (String) token;
                            if (string.length() > 0) {
                                if(xmlXsiTypeConverter != null) {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                            stringToValue(string, xmlXsiTypeConverter));//give a name
                                } else {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                            config.isKeepStrings() ? string : stringToValue(string));
                                }
                            }

                        } else if (token == LT) {
                            // Nested element
                            // since no need to the replacement, original parse function is ok
                            if (parse(x, jsonObject, tagName, config)) {
                                if (config.getForceList().contains(tagName)) {
                                    // Force the value to be an array
                                    if (jsonObject.length() == 0) {
                                        context.put(tagName, new JSONArray());
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {//change
                                        context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.append(tagName, jsonObject);
                                    }
                                } else {
                                    if (jsonObject.length() == 0) {
                                        context.accumulate(tagName, "");
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.accumulate(tagName, jsonObject);
                                    }
                                }

                                return false;
                            }
                        }
                    }
                } else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }else{//in this branch means that the recursive is on the key path
            if (position==tokens.length-1){//if the function just reach the position, it needs to replace the object directly
                //and skip the original sub object in the xml
                    tagName = (String) token;//read the name
                    token = null;
                    jsonObject = new JSONObject();//a new Object
                    boolean nilAttributeFound = false;//null attribute?
                    xmlXsiTypeConverter = null;//type changer?
                if (config.getForceList().contains(tagName)) {
                    // Force the value to be an array
                    // do the replacement directly
                    if (replace.length() == 0) {
                        context.put(tagName, new JSONArray());
                    } else if (replace.length() == 1
                            && replace.opt(config.getcDataTagName()) != null) {//change
                        context.append(tagName, replace.get(tagName));
                    } else {
                        context.append(tagName, replace.get(tagName));
                    }
                } else {
                    //do the replacement directly
                    if (replace.length() == 0) {
                        context.accumulate(tagName, "");
                    } else if (replace.length() == 1
                            && replace.opt(config.getcDataTagName()) != null) {
                        context.accumulate(tagName, replace.get(tagName));
                    } else {
                        context.accumulate(tagName, replace.get(tagName));
                    }
                }
                //the part below is used to skip the original text in the xml
                    for (;;) {
                        if (token == null) {
                            token = x.nextToken();
                        }
                        // attribute = value
                        // just do nothing, the code doesn't add any new json object in the context
                        if (token instanceof String) {
                            string = (String) token;
                            token = x.nextToken();//attribute
                            if (token == EQ) {
                                token = x.nextToken();
                                if (!(token instanceof String)) {
                                    throw x.syntaxError("Missing value");//if no value?
                                }
//                                if (config.isConvertNilAttributeToNull()//based on the xml, the rules are complex
//                                        && NULL_ATTR.equals(string)
//                                        && Boolean.parseBoolean((String) token)) {
//                                    nilAttributeFound = true;//a null attribute
//                                } else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
//                                        && TYPE_ATTR.equals(string)) {//type transformation
//                                    xmlXsiTypeConverter = config.getXsiTypeMap().get(token);//get the type converter
//                                } else if (!nilAttributeFound) {
//                                    //System.out.println(jsonObject);
//                                    jsonObject.accumulate(string,
//                                            config.isKeepStrings()
//                                                    ? ((String) token)
//                                                    : stringToValue((String) token));//change to value
//                                    // System.out.println(jsonObject);
//                                }
                                token = null;
                            } else {//empty
                                //jsonObject.accumulate(string, "");
                            }


                        } else if (token == SLASH) {
                            // Empty tag <.../>
                            // similarly, do nothing, just skip
                            if (x.nextToken() != GT) {
                                throw x.syntaxError("Misshaped tag");
                            }
//                            if (config.getForceList().contains(tagName)) {//???
//                                // Force the value to be an array
//                                if (nilAttributeFound) {
//                                    context.append(tagName, JSONObject.NULL);
//                                } else if (jsonObject.length() > 0) {
//                                    context.append(tagName, jsonObject);
//                                } else {
//                                    context.put(tagName, new JSONArray());//at first, there is no length
//                                }
//                            } else {
//                                if (nilAttributeFound) {
//                                    context.accumulate(tagName, JSONObject.NULL);
//                                } else if (jsonObject.length() > 0) {
//                                    context.accumulate(tagName, jsonObject);
//                                } else {
//                                    context.accumulate(tagName, "");//under a key
//                                }
//                            }
                            return false;

                        } else if (token == GT) {//normal
                            // Content, between <...> and </...>
                            for (;;) {
                                token = x.nextContent();
//                        System.out.println(token); iterate all the parts of the current elements read string or <
                                if (token == null) {
                                    if (tagName != null) {
                                        throw x.syntaxError("Unclosed tag " + tagName);
                                    }
                                    return false;//with out content
                                } else if (token instanceof String) {
                                    //it means that there is no nested element
                                    // just skip to the end of this tag directly
                                    x.skipPast("</"+tagName);
                                    x.skipPast(">");
                                    return false;
//                                    string = (String) token;
//                                    if (string.length() > 0) {
//                                        if(xmlXsiTypeConverter != null) {
//                                            jsonObject.accumulate(config.getcDataTagName(),
//                                                    stringToValue(string, xmlXsiTypeConverter));//give a name
//                                        } else {
//                                            jsonObject.accumulate(config.getcDataTagName(),
//                                                    config.isKeepStrings() ? string : stringToValue(string));
//                                        }
//                                    }

                                } else if (token == LT) {
                                    // Nested element
                                    // similarly, for nested elements, it can just skip over the current tag
                                    x.skipPast("</"+tagName);
                                    x.skipPast(">");
                                    return false;
//                                    if (parse(x, jsonObject, tagName, config)) {
//                                        if (config.getForceList().contains(tagName)) {
//                                            // Force the value to be an array
//                                            if (jsonObject.length() == 0) {
//                                                context.put(tagName, new JSONArray());
//                                            } else if (jsonObject.length() == 1
//                                                    && jsonObject.opt(config.getcDataTagName()) != null) {//change
//                                                context.append(tagName, jsonObject.opt(config.getcDataTagName()));
//                                            } else {
//                                                context.append(tagName, jsonObject);
//                                            }
//                                        } else {
//                                            //System.out.println(jsonObject);
//                                            if (jsonObject.length() == 0) {
//                                                context.accumulate(tagName, "");
//                                            } else if (jsonObject.length() == 1
//                                                    && jsonObject.opt(config.getcDataTagName()) != null) {
//                                                context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
//                                            } else {
//                                                context.accumulate(tagName, jsonObject);
//                                            }
//                                        }
//
//                                        return false;
//                                    }
                                }
                            }
                        } else {
                            throw x.syntaxError("Misshaped tag");
                        }
                    }

                }
            /*
            If the function now is on the key path, but doesn't reach the end of the path
            Do the same operation as the last part of the original parse function
            The only difference is this part will use this parse function for recursion rather than the original
            parse function
             */
            else{
                    tagName = (String) token;//read the name
                    token = null;
                    jsonObject = new JSONObject();//a new Object
                    boolean nilAttributeFound = false;//null attribute?
                    xmlXsiTypeConverter = null;//type changer?
                    for (;;) {
                        if (token == null) {
                            token = x.nextToken();
                        }
                        // attribute = value
                        if (token instanceof String) {
                            string = (String) token;
                            token = x.nextToken();//attribute
                            if (token == EQ) {
                                token = x.nextToken();
                                if (!(token instanceof String)) {
                                    throw x.syntaxError("Missing value");//if no value?
                                }
                                if (config.isConvertNilAttributeToNull()//based on the xml, the rules are complex
                                        && NULL_ATTR.equals(string)
                                        && Boolean.parseBoolean((String) token)) {
                                    nilAttributeFound = true;//a null attribute
                                } else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                                        && TYPE_ATTR.equals(string)) {//type transformation
                                    xmlXsiTypeConverter = config.getXsiTypeMap().get(token);//get the type converter
                                } else if (!nilAttributeFound) {
                                    jsonObject.accumulate(string,
                                            config.isKeepStrings()
                                                    ? ((String) token)
                                                    : stringToValue((String) token));//change to value
                                }
                                token = null;
                            } else {//empty
                                jsonObject.accumulate(string, "");
                            }


                        } else if (token == SLASH) {
                            // Empty tag <.../>
                            if (x.nextToken() != GT) {
                                throw x.syntaxError("Misshaped tag");
                            }
                            if (config.getForceList().contains(tagName)) {//???
                                // Force the value to be an array
                                if (nilAttributeFound) {
                                    context.append(tagName, JSONObject.NULL);
                                } else if (jsonObject.length() > 0) {
                                    context.append(tagName, jsonObject);
                                } else {
                                    context.put(tagName, new JSONArray());//at first, there is no length
                                }
                            } else {
                                if (nilAttributeFound) {
                                    context.accumulate(tagName, JSONObject.NULL);
                                } else if (jsonObject.length() > 0) {
                                    context.accumulate(tagName, jsonObject);
                                } else {
                                    context.accumulate(tagName, "");//under a key
                                }
                            }
                            return false;

                        } else if (token == GT) {//normal
                            // Content, between <...> and </...>
                            for (;;) {
                                token = x.nextContent();
                                if (token == null) {
                                    if (tagName != null) {
                                        throw x.syntaxError("Unclosed tag " + tagName);
                                    }
                                    return false;//with out content
                                } else if (token instanceof String) {
                                    string = (String) token;
                                    if (string.length() > 0) {
                                        if(xmlXsiTypeConverter != null) {
                                            jsonObject.accumulate(config.getcDataTagName(),
                                                    stringToValue(string, xmlXsiTypeConverter));//give a name
                                        } else {
                                            jsonObject.accumulate(config.getcDataTagName(),
                                                    config.isKeepStrings() ? string : stringToValue(string));
                                        }
                                    }

                                } else if (token == LT) {
                                    // Nested element
                                    // the position is added by 1, to search the next token in the key path, like bfs
                                    if (parse(x, jsonObject, tagName, config,tokens,position+1,replace)) {
                                        if (config.getForceList().contains(tagName)) {
                                            // Force the value to be an array
                                            if (jsonObject.length() == 0) {
                                                context.put(tagName, new JSONArray());
                                            } else if (jsonObject.length() == 1
                                                    && jsonObject.opt(config.getcDataTagName()) != null) {//change
                                                context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                                            } else {
                                                context.append(tagName, jsonObject);
                                            }
                                        } else {
                                            if (jsonObject.length() == 0) {
                                                context.accumulate(tagName, "");
                                            } else if (jsonObject.length() == 1
                                                    && jsonObject.opt(config.getcDataTagName()) != null) {
                                                context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
                                            } else {
                                                context.accumulate(tagName, jsonObject);
                                            }
                                        }

                                        return false;
                                    }
                                }
                            }
                        } else {
                            throw x.syntaxError("Misshaped tag");
                        }
                    }

                }
        }
    }

    /**
     * This method tries to convert the given string value to the target object
     * @param string String to convert
     * @param typeConverter value converter to convert string to integer, boolean e.t.c
     * @return JSON value of this string or the string
     */
    public static Object stringToValue(String string, XMLXsiTypeConverter<?> typeConverter) {
        if(typeConverter != null) {
            return typeConverter.convert(string);
        }
        return stringToValue(string);
    }

    /**
     * This method is the same as {@link JSONObject#stringToValue(String)}.
     *
     * @param string String to convert
     * @return JSON value of this string or the string
     */
    // To maintain compatibility with the Android API, this method is a direct copy of
    // the one in JSONObject. Changes made here should be reflected there.
    // This method should not make calls out of the XML object.
    public static Object stringToValue(String string) {
        if ("".equals(string)) {
            return string;
        }

        // check JSON key words true/false/null
        if ("true".equalsIgnoreCase(string)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(string)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(string)) {
            return JSONObject.NULL;
        }

        /*
         * If it might be a number, try converting it. If a number cannot be
         * produced, then the value will just be a string.
         */

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                return stringToNumber(string);
            } catch (Exception ignore) {
            }
        }
        return string;
    }
    
    /**
     * direct copy of {@link JSONObject#stringToNumber(String)} to maintain Android support.
     */
    private static Number stringToNumber(final String val) throws NumberFormatException {
        char initial = val.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            // decimal representation
            if (isDecimalNotation(val)) {
                // Use a BigDecimal all the time so we keep the original
                // representation. BigDecimal doesn't support -0.0, ensure we
                // keep that by forcing a decimal.
                try {
                    BigDecimal bd = new BigDecimal(val);
                    if(initial == '-' && BigDecimal.ZERO.compareTo(bd)==0) {
                        return Double.valueOf(-0.0);
                    }
                    return bd;
                } catch (NumberFormatException retryAsDouble) {
                    // this is to support "Hex Floats" like this: 0x1.0P-1074
                    try {
                        Double d = Double.valueOf(val);
                        if(d.isNaN() || d.isInfinite()) {
                            throw new NumberFormatException("val ["+val+"] is not a valid number.");
                        }
                        return d;
                    } catch (NumberFormatException ignore) {
                        throw new NumberFormatException("val ["+val+"] is not a valid number.");
                    }
                }
            }
            // block items like 00 01 etc. Java number parsers treat these as Octal.
            if(initial == '0' && val.length() > 1) {
                char at1 = val.charAt(1);
                if(at1 >= '0' && at1 <= '9') {
                    throw new NumberFormatException("val ["+val+"] is not a valid number.");
                }
            } else if (initial == '-' && val.length() > 2) {
                char at1 = val.charAt(1);
                char at2 = val.charAt(2);
                if(at1 == '0' && at2 >= '0' && at2 <= '9') {
                    throw new NumberFormatException("val ["+val+"] is not a valid number.");
                }
            }
            // integer representation.
            // This will narrow any values to the smallest reasonable Object representation
            // (Integer, Long, or BigInteger)
            
            // BigInteger down conversion: We use a similar bitLength compare as
            // BigInteger#intValueExact uses. Increases GC, but objects hold
            // only what they need. i.e. Less runtime overhead if the value is
            // long lived.
            BigInteger bi = new BigInteger(val);
            if(bi.bitLength() <= 31){
                return Integer.valueOf(bi.intValue());
            }
            if(bi.bitLength() <= 63){
                return Long.valueOf(bi.longValue());
            }
            return bi;
        }
        throw new NumberFormatException("val ["+val+"] is not a valid number.");
    }
    
    /**
     * direct copy of {@link JSONObject#isDecimalNotation(String)} to maintain Android support.
     */
    private static boolean isDecimalNotation(final String val) {
        return val.indexOf('.') > -1 || val.indexOf('e') > -1
                || val.indexOf('E') > -1 || "-0".equals(val);
    }


    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code 
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * @param string
     *            The source string.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string) throws JSONException {
        return toJSONObject(string, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * toJSONObject for find sub json object under specified key path. This function reused part
     * of the code in parse function and skipPast function to find the sub object.
     *
     * @param reader The source reader
     * @param path The JSONPointer contains key path
     * @return The sub json object under the key
     * @throws JSONException Thrown if the key path doesn't exist or there are errors while parsing the reader
     */

    public static JSONObject toJSONObject(Reader reader, JSONPointer path)
            throws JSONException{
        try{
        JSONObject jo = new JSONObject();
        XMLTokener x = new XMLTokener(reader);
        String tokens[] = path.toString().split("/");//tokens on the key path
        XMLParserConfiguration config = XMLParserConfiguration.ORIGINAL;
        while (x.more()) {
            if (tokens.length==0){
                while (x.more()) {
                    x.skipPast("<");//pass the token
                    if(x.more()) {//similar to next()
                        parse(x, jo, null, config);
                    }
                }
                return jo;
            }
            for (int i=1;i<tokens.length-1;i++) {
                //System.out.println("t:"+tokens[i]);
                while (true) {
                    x.skipPast("<");
                    Object res = x.nextToken();
                    if (res instanceof String && res.equals(tokens[i]))
                        break;
                }
                x.skipPast(">");

            }//for the last token, the function needs to include it in the result
            Object token = tokens[tokens.length-1];
            String string;
            //this part is the last part of code in the parse function, which uses recursion to generate json object
            //I copy and modify the code to extract the sub object
            while (true){
                x.skipPast("<");
                Object res = x.nextToken();
                if (res instanceof String && res.equals(tokens[tokens.length-1]))
                    break;
            }
            {
                String tagName = (String) token;//read the name
                token = null;
                JSONObject jsonObject = new JSONObject();//a new Object
                boolean nilAttributeFound = false;//null attribute
                XMLXsiTypeConverter xmlXsiTypeConverter = null;//type changer
                //to iterate to get the sub object
                for (;;) {
                    if (token == null) {
                        token = x.nextToken();
                    }
                    // attribute = value
                    if (token instanceof String) {
                        string = (String) token;
                        token = x.nextToken();//attribute
                        if (token == EQ) {
                            token = x.nextToken();
                            if (!(token instanceof String)) {
                                throw x.syntaxError("Missing value");//if no value
                            }
                            //these are the complex rules used in parse function
                            if (config.isConvertNilAttributeToNull()
                                    && NULL_ATTR.equals(string)
                                    && Boolean.parseBoolean((String) token)) {
                                nilAttributeFound = true;
                            } else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                                    && TYPE_ATTR.equals(string)) {
                                xmlXsiTypeConverter = config.getXsiTypeMap().get(token);//get the type converter
                            } else if (!nilAttributeFound) {
                                jsonObject.accumulate(string,
                                        config.isKeepStrings()
                                                ? ((String) token)
                                                : stringToValue((String) token));//change to value
                            }
                            token = null;
                        } else {//if there are no attributes
                            jsonObject.accumulate(string, "");
                        }


                    } else if (token == SLASH) {//for empty element
                        // Empty tag <.../>
                        if (x.nextToken() != GT) {
                            throw x.syntaxError("Misshaped tag");
                        }
                        if (config.getForceList().contains(tagName)) {//if it is forced to be an JSONArray
                            // Force the value to be an array
                            if (nilAttributeFound) {
                                jo.append(tagName, JSONObject.NULL);
                            } else if (jsonObject.length() > 0) {
                                jo.append(tagName, jsonObject);
                            } else {
                                jo.put(tagName, new JSONArray());
                            }
                        } else {
                            if (nilAttributeFound) {
                                jo.accumulate(tagName, JSONObject.NULL);
                            } else if (jsonObject.length() > 0) {
                                jo.accumulate(tagName, jsonObject);
                            } else {
                                jo.accumulate(tagName, "");
                            }
                        }

                    } else if (token == GT) {
                        // Content, between <...> and </...>
                        //iterate until generate the whole sub object
                        for (;;) {
                            token = x.nextContent();
                            if (token == null) {
                                if (tagName != null) {
                                    throw x.syntaxError("Unclosed tag " + tagName);
                                }
                            } else if (token instanceof String) {//not nested, the element only have string value
                                string = (String) token;
                                if (string.length() > 0) {
                                    if(xmlXsiTypeConverter != null) {
                                        jsonObject.accumulate(config.getcDataTagName(),
                                                stringToValue(string, xmlXsiTypeConverter));//give a name
                                    } else {
                                        jsonObject.accumulate(config.getcDataTagName(),
                                                config.isKeepStrings() ? string : stringToValue(string));
                                    }
                                }

                            } else if (token == LT) {
                                // Nested element
                                if (parse(x, jsonObject, tagName, config)) {
                                    if (config.getForceList().contains(tagName)) {
                                        // Force the value to be an array
                                        if (jsonObject.length() == 0) {
                                            jo.put(tagName, new JSONArray());
                                        } else if (jsonObject.length() == 1
                                                && jsonObject.opt(config.getcDataTagName()) != null) {//change
                                            jo.append(tagName, jsonObject.opt(config.getcDataTagName()));
                                        } else {
                                            jo.append(tagName, jsonObject);
                                        }
                                    } else {
                                        if (jsonObject.length() == 0) {
                                            jo.accumulate(tagName, "");
                                        } else if (jsonObject.length() == 1
                                                && jsonObject.opt(config.getcDataTagName()) != null) {
                                            jo.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
                                        } else {
                                            jo.accumulate(tagName, jsonObject);
                                        }
                                    }
                                    try {
                                        return jo;
                                    }catch (Exception e){
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    } else {
                        throw x.syntaxError("Misshaped tag");
                    }
                }
            }
        }
        }
        catch (JSONException e){
            System.out.println(e);
            System.out.println("Can't find the key path in the xml");
            throw e;
        }catch (Exception e){
            System.out.println("parse error");
        }
        return null;
    }

    /**
     * toJSONObject to replace sub json object under specified key path. This function reused part
     * of the code in parse function and skipPast function to find the sub object. And it reused toJSONObject function
     * for finding the sub object to detect if a key path exists.
     *
     * @param reader The source reader
     * @param path The key path to replace
     * @param replacement The json object used to replace the sub object
     * @return The whole json object after replacing
     */
    public static JSONObject toJSONObject(Reader reader, JSONPointer path, JSONObject replacement)
    {
        try{
            toJSONObject(reader,path); //try to find the path first, if not exists, just stop parsing, which will save time
        }catch (JSONException e){
            //e.printStackTrace();
            System.out.println("the key path doesn't exist");
            throw e;
        }
        try{
        reader.reset();//go back to the start
        String[] tokens = path.toString().split("/");//get the path tokens
            if (tokens.length==0){
                return replacement;
            }
        JSONObject jo = new JSONObject();
        XMLTokener x = new XMLTokener(reader);
        while (x.more()) {
            x.skipPast("<");//pass the token
            if(x.more()) {//similar to next()
                parse(x, jo, null, XMLParserConfiguration.ORIGINAL,tokens,1,replacement);
            }
        }
        return jo;}
        catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * toJSONObject to transform key values in xml files. This method
     * is just same to the toJSONObject method toJSONObject(Reader reader,
     * XMLParserConfiguration config) except it accepts a keyTransformer Function
     * Object to do the transformation. To finish the transformation, I reloaded
     * a new parse method to use transformer during parsing.
     *
     * @param reader The source reader
     * @param keyTransformer The transformer will read a String and return a transformed string.
     * @return The whole json object after transforming
     */
    public static JSONObject toJSONObject(Reader reader, Function<String,String> keyTransformer){
        JSONObject jo = new JSONObject();
        XMLTokener x = new XMLTokener(reader);
        while (x.more()) {
            x.skipPast("<");//pass the token
            if(x.more()) {//similar to next()
                parse(x, jo, null, XMLParserConfiguration.ORIGINAL,keyTransformer);
            }
        }
        return jo;
    }


    /**
     * Scan the content following the named tag, attaching it to the context. This
     * is overloaded for toJSONObject(Reader reader, Function<String,String> keyTransformer).
     * The codes are just the same as original parse method. The only difference is that
     * when methods like context.accumulate(tagName, jsonObject) are used, the key transformer is
     * used on the tagName to transform it to what we want.
     *
     * @param x
     *            The XMLTokener containing the source string.
     * @param context
     *            The JSONObject that will include the new material.
     * @param name
     *            The tag name.
     * @param  config
     *            The config tells the parse method how to parse XML
     * @param  keyTransformer
     *            The transformer is used to transform the keys in the parsing.
     * @return true if the close tag is processed.
     * @throws JSONException
     */
    private static boolean parse(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config, Function<String,String> keyTransformer)
            throws JSONException{
        char c;
        int i;
        JSONObject jsonObject = null;
        String string;
        String tagName;
        Object token;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter; //change interface

        // Test for and skip past these forms:
        // <!-- ... -->
        // <! ... >
        // <![ ... ]]>
        // <? ... ?>
        // Report errors for these forms:
        // <>
        // <=
        // <<

        token = x.nextToken();
//        System.out.println(token);
        // <!

        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;//pass
                }
                x.back();
            } else if (c == '[') {
                token = x.nextToken();//token inside the xml symbols
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            //use the transformer
                            context.accumulate(keyTransformer.apply(config.getcDataTagName()), string);
                        }
                        return false;//cdata
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();//pass the section
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");//Returns the next XML meta token. This is used for skipping over <!...>and <?...?> structures.
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        } else if (token == QUEST) {

            // <?
            x.skipPast("?>");
            return false;
        } else if (token == SLASH) {

            // Close tag </

            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);//the name and the tag is different
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;//close the token

        } else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");//impossible

            // Open tag <

        } else {
            tagName = (String) token;//read the name
            token = null;
            jsonObject = new JSONObject();//a new Object
            boolean nilAttributeFound = false;//null attribute?
            xmlXsiTypeConverter = null;//type changer?
            for (;;) {
                if (token == null) {
                    token = x.nextToken();
            //read >
                }
                // attribute = value
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();//attribute
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");//if no value?
                        }
                        if (config.isConvertNilAttributeToNull()//based on the xml, the rules are complex
                                && NULL_ATTR.equals(string)
                                && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;//a null attribute
                        } else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                                && TYPE_ATTR.equals(string)) {//type transformation
                            xmlXsiTypeConverter = config.getXsiTypeMap().get(token);//get the type converter
                        } else if (!nilAttributeFound) {
                            //System.out.println(jsonObject);
                            jsonObject.accumulate(keyTransformer.apply(string),
                                    config.isKeepStrings()
                                            ? ((String) token)
                                            : stringToValue((String) token));//change to value
                            // System.out.println(jsonObject);
                        }
                        token = null;
                    } else {//empty
                        jsonObject.accumulate(keyTransformer.apply(string), "");
                    }


                } else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (config.getForceList().contains(tagName)) {//???
                        // Force the value to be an array
                        if (nilAttributeFound) {
                            context.append(keyTransformer.apply(tagName), JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(keyTransformer.apply(tagName), jsonObject);
                        } else {
                            context.put(keyTransformer.apply(tagName), new JSONArray());//at first, there is no length
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(keyTransformer.apply(tagName), JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(keyTransformer.apply(tagName), jsonObject);
                        } else {
                            context.accumulate(keyTransformer.apply(tagName), "");//under a key
                        }
                    }
                    return false;

                } else if (token == GT) {//normal
                    // Content, between <...> and </...>
                    for (;;) {
                        token = x.nextContent();
//                        System.out.println(token); iterate all the parts of the current elements read string or <
                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;//with out content
                        } else if (token instanceof String) {
//                            System.out.println(token); when this is a string
                            string = (String) token;
                            if (string.length() > 0) {
                                if(xmlXsiTypeConverter != null) {
                                    jsonObject.accumulate(keyTransformer.apply(config.getcDataTagName()),
                                            stringToValue(string, xmlXsiTypeConverter));//give a name
//                                    jsonObject.accumulate("a",
//                                            stringToValue(string, xmlXsiTypeConverter));//give a name
                                } else {
                                    jsonObject.accumulate(keyTransformer.apply(config.getcDataTagName()),
                                            config.isKeepStrings() ? string : stringToValue(string));

                                }
                            }

                        } else if (token == LT) {
                            // Nested element
//                            System.out.println(token); nested elements
//                            System.out.println(jsonObject); //DFS strange output
                            if (parse(x, jsonObject, tagName, config,keyTransformer)) {
                                if (config.getForceList().contains(tagName)) {
                                    // Force the value to be an array
//                                    System.out.println(tagName);
                                    if (jsonObject.length() == 0) {
                                        context.put(keyTransformer.apply(tagName), new JSONArray());
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(keyTransformer.apply(config.getcDataTagName())) != null) {//change
                                        //System.out.println(jsonObject.opt(config.getcDataTagName()));
                                        System.out.println(config.getcDataTagName());
                                        context.append(keyTransformer.apply(tagName), jsonObject.opt(keyTransformer.apply(config.getcDataTagName())));
                                    } else {
                                        context.append(keyTransformer.apply(tagName), jsonObject);
                                    }
                                } else {
                                    //System.out.println(jsonObject);
                                    if (jsonObject.length() == 0) {
                                        context.accumulate(keyTransformer.apply(tagName), "");
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(keyTransformer.apply(config.getcDataTagName())) != null) {
//                                        System.out.println("before:"+jsonObject);
//                                        System.out.println(config.getcDataTagName());
//                                        System.out.println(tagName);
                                        //System.out.println(jsonObject.opt(config.getcDataTagName()));

                                        context.accumulate(keyTransformer.apply(tagName), jsonObject.opt(keyTransformer.apply(config.getcDataTagName())));

//                                        System.out.println("after:"+context);
                                    } else {
//                                        System.out.println(context);
//                                        System.out.println(tagName);
//                                        System.out.println(jsonObject);
                                        context.accumulate(keyTransformer.apply(tagName), jsonObject);
//                                        System.out.println(context);
                                    }
                                }

                                return false;
                            }
                        }
                    }
                } else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code 
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * @param reader The XML source reader.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader) throws JSONException {
        return toJSONObject(reader, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param reader The XML source reader.
     * @param keepStrings If true, then values will not be coerced into boolean
     *  or numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader, boolean keepStrings) throws JSONException {
        if(keepStrings) {
            return toJSONObject(reader, XMLParserConfiguration.KEEP_STRINGS);
        }
        return toJSONObject(reader, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param reader The XML source reader.
     * @param config Configuration options for the parser
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader, XMLParserConfiguration config) throws JSONException {
        JSONObject jo = new JSONObject();
        XMLTokener x = new XMLTokener(reader);
        while (x.more()) {
            x.skipPast("<");//pass the token
            if(x.more()) {//similar to next()
                parse(x, jo, null, config);
            }
        }
        return jo;
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code 
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param string
     *            The source string.
     * @param keepStrings If true, then values will not be coerced into boolean
     *  or numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string, boolean keepStrings) throws JSONException {
        return toJSONObject(new StringReader(string), keepStrings);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code 
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param string
     *            The source string.
     * @param config Configuration options for the parser.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string, XMLParserConfiguration config) throws JSONException {
        return toJSONObject(new StringReader(string), config);
    }


    //https://www.baeldung.com/java-future
    /**
     * toJSONObject to transform key values in xml files. This method
     * is an asynchronous toJSONObject method. It accepts a reader and
     * invoke normal toJSONObject method to do the transformation.
     * The only difference is that this method uses Future and Thread poll
     * to do the transformation asynchronously.
     *
     * @param reader The source reader
     * @param onFinish The callback function for success.
     * @param onError The callback function for exception.
     * @return The whole json object after transforming or null when there is an exception.
     */
    public static Future<JSONObject> toJSONObject(Reader reader, Function<JSONObject,Void> onFinish, Function<Exception,Void> onError){
        return executor.submit(new Callable<JSONObject>() {
            @Override
            public JSONObject call(){
                try {
                    JSONObject jsonObject = XML.toJSONObject(reader);
                    onFinish.apply(jsonObject);
                    return jsonObject;
                }catch (Exception e){
                    //e.printStackTrace();
                    onError.apply(e);
                }
                return null;
            }
        });
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(Object object) throws JSONException {
        return toString(object, null, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.toS
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName) {
        return toString(object, tagName, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @param config
     *            Configuration that can control output to XML.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName, final XMLParserConfiguration config)
            throws JSONException {
        StringBuilder sb = new StringBuilder();
        JSONArray ja;
        JSONObject jo;
        String string;

        if (object instanceof JSONObject) {

            // Emit <tagName>
            if (tagName != null) {
                sb.append('<');
                sb.append(tagName);
                sb.append('>');
            }

            // Loop thru the keys.
            // don't use the new entrySet accessor to maintain Android Support
            jo = (JSONObject) object;
            for (final String key : jo.keySet()) {
                Object value = jo.opt(key);
                if (value == null) {
                    value = "";
                } else if (value.getClass().isArray()) {
                    value = new JSONArray(value);
                }

                // Emit content in body
                if (key.equals(config.getcDataTagName())) {
                    if (value instanceof JSONArray) {
                        ja = (JSONArray) value;
                        int jaLength = ja.length();
                        // don't use the new iterator API to maintain support for Android
						for (int i = 0; i < jaLength; i++) {
                            if (i > 0) {
                                sb.append('\n');
                            }
                            Object val = ja.opt(i);
                            sb.append(escape(val.toString()));
                        }
                    } else {
                        sb.append(escape(value.toString()));
                    }

                    // Emit an array of similar keys

                } else if (value instanceof JSONArray) {
                    ja = (JSONArray) value;
                    int jaLength = ja.length();
                    // don't use the new iterator API to maintain support for Android
					for (int i = 0; i < jaLength; i++) {
                        Object val = ja.opt(i);
                        if (val instanceof JSONArray) {
                            sb.append('<');
                            sb.append(key);
                            sb.append('>');
                            sb.append(toString(val, null, config));
                            sb.append("</");
                            sb.append(key);
                            sb.append('>');
                        } else {
                            sb.append(toString(val, key, config));
                        }
                    }
                } else if ("".equals(value)) {
                    sb.append('<');
                    sb.append(key);
                    sb.append("/>");

                    // Emit a new tag <k>

                } else {
                    sb.append(toString(value, key, config));
                }
            }
            if (tagName != null) {

                // Emit the </tagName> close tag
                sb.append("</");
                sb.append(tagName);
                sb.append('>');
            }
            return sb.toString();

        }

        if (object != null && (object instanceof JSONArray ||  object.getClass().isArray())) {
            if(object.getClass().isArray()) {
                ja = new JSONArray(object);
            } else {
                ja = (JSONArray) object;
            }
            int jaLength = ja.length();
            // don't use the new iterator API to maintain support for Android
			for (int i = 0; i < jaLength; i++) {
                Object val = ja.opt(i);
                // XML does not have good support for arrays. If an array
                // appears in a place where XML is lacking, synthesize an
                // <array> element.
                sb.append(toString(val, tagName == null ? "array" : tagName, config));
            }
            return sb.toString();
        }

        string = (object == null) ? "null" : escape(object.toString());
        return (tagName == null) ? "\"" + string + "\""
                : (string.length() == 0) ? "<" + tagName + "/>" : "<" + tagName
                        + ">" + string + "</" + tagName + ">";

    }
}
