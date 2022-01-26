import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;

import org.json.JSONException;
import org.json.JSONPointer;
import org.json.JSONObject;
import org.json.XML;

public class M2TestForFile {
    public static void main(String[] args) {


        try {
            BufferedReader reader = new BufferedReader(new FileReader("sample.xml"));
//            JSONObject jobj = XML.toJSONObject(new StringReader(xmlString), new JSONPointer("/contact/address/street/"));
            JSONObject jobj = XML.toJSONObject(reader, new JSONPointer("/catalog/"));
//            JSONObject jobj = XML.toJSONObject(new StringReader(xmlString));
            System.out.println(jobj);
//            System.out.println(jobj);
            reader.close();
        } catch (JSONException e) {
            System.out.println(e);
        }catch (Exception e){

        }

        System.out.println("-----------------------");

        try {
            BufferedReader reader = new BufferedReader(new FileReader("sample.xml"));
            JSONObject replacement = XML.toJSONObject("<catalog>Ave of the Arts</catalog>\n");
            System.out.println("Given replacement: " + replacement);
//            JSONObject jobj = XML.toJSONObject(new StringReader(xmlString), new JSONPointer("/contact/address/street/"), replacement);
            JSONObject jobj = XML.toJSONObject(reader, new JSONPointer("/catalog/"), replacement);
            System.out.println(jobj);
            reader.close();
        } catch (JSONException e) {
            System.out.println(e);
        }catch (Exception e){

        }
    }


}
