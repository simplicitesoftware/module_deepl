package com.simplicite.commons.Deepl;

import kong.unirest.HttpResponse;
import java.util.*;

import org.json.JSONObject;
import org.json.JSONArray;

import com.simplicite.util.*;
import com.simplicite.util.exceptions.*;
import com.simplicite.util.tools.*;

import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;


/**
 * Shared code DeeplTool
 */
public class DeeplTool implements java.io.Serializable {
	private static final long serialVersionUID = 1L;
	private static final String DEEPL_AUTH_KEY = Grant.getSystemAdmin().getParameter("DEEPL_API_KEY");
	private static final String DEEPL_API_URL = Grant.getSystemAdmin().getParameter("DEEPL_API_URL");
	private static final String JSON_KEY_TRANSLATION ="translations";
	private static final String FIELD_LIST_OBJECT ="FieldList";
	private static final String LOV_NAME_FIELD ="lov_name";
	private static final String LOV_LIST_ID_FIELD ="lov_list_id";
	private static final String LOV_CODE_FIELD ="lov_code";
	/**
	 * Calls the DeepL API to translate a list of sentences to the specified language.
	 * 
	 * @param sentences the list of sentences to be translated
	 * @param lang the target language for translation
	 * @param g the Grant object for logging purposes
	 * @return the translated text as a String
	 * @throws HTTPException if an error occurs during the API call
	 */
	private static HashMap<String, String> isoToDeeplMap;
	static {
		isoToDeeplMap = new HashMap<>();
		isoToDeeplMap.put("CZE", "CS");
		isoToDeeplMap.put("GER", "DE");
		isoToDeeplMap.put("ENU", "EN");
		isoToDeeplMap.put("ESP", "ES");
		isoToDeeplMap.put("FRA", "FR");
		isoToDeeplMap.put("HUN", "HU");
		isoToDeeplMap.put("ITA", "IT");
		isoToDeeplMap.put("JPN", "JA");
		isoToDeeplMap.put("KOR", "KO");
		isoToDeeplMap.put("DUT", "NL");
		isoToDeeplMap.put("POL", "PL");
		isoToDeeplMap.put("POR", "PT");
		isoToDeeplMap.put("RUM", "RO");
		isoToDeeplMap.put("RUS", "RU");
		isoToDeeplMap.put("SLO", "SK");
		isoToDeeplMap.put("TUR", "TR");
		isoToDeeplMap.put("UKR", "UK");
		isoToDeeplMap.put("CHI", "ZH");

	}
	public static String callDeepl(List<String> sentences,String lang,Grant g) throws HTTPException{
		return callDeepl(sentences,null, lang, g);
	}
	public static String callDeepl(List<String> sentences,String fromLang,String lang,Grant g) throws HTTPException{
		try {
			JSONObject requestBody = new JSONObject().put("text", sentences ).put("target_lang",lang);
			if(!Tool.isEmpty(fromLang)){
				requestBody.put("source_lang", fromLang);
			}
            HttpResponse<JsonNode> response = Unirest.post(DEEPL_API_URL)
					.header("Authorization", "DeepL-Auth-Key " + DEEPL_AUTH_KEY)
                    //.header("User-Agent", "deeplSimplicite/1.2.3")
					.header("Content-Type", "application/json")
					.body(requestBody.toString())
                    .asJson();
				int status = response.getStatus();
				
				switch(status){
					case 200:
						JSONObject jsonResponse = new JSONObject(response.getBody().getObject().toString()) ;
						AppLog.info("response: "+jsonResponse.toString(1),g);
						if(jsonResponse.has(JSON_KEY_TRANSLATION) && jsonResponse.getJSONArray(JSON_KEY_TRANSLATION).getJSONObject(0).has("text") ){
							return jsonResponse.getJSONArray(JSON_KEY_TRANSLATION).getJSONObject(0).getString("text");
						}

						return response.getBody().getObject().toString();
						
					case 403:
						throw new HTTPException(status,"Access forbidden. Check your authentication credentials.");
						
					case 404:
						throw new HTTPException(status,"The requested resource was not found.");
					
					case 429:
						throw new HTTPException(status,"Too many requests");
						
					case 456:
						throw new HTTPException(status,"Quota exceeded");
					
					default:
						if(status > 500){
							throw new HTTPException(status,"Temporary errors in the DeepL service.");
						}
						throw new HTTPException(status,"Received an unexpected status code");
						
				}
				
            
        } catch (UnirestException e) {
            AppLog.error(e, g);
        }
		return "error";
		
	}

	/**
	 * Adds a language to a specified list.
	 * Use in automatic translation if language is not already in the usr list.
	 * 
	 * @param listName The name of the list.
	 * @param langIso The ISO code of the language.
	 * @param g The Grant object.
	 */
	public static void addLangList(String listName,String langIso,Grant g){
		ObjectDB obj = g.getTmpObject(FIELD_LIST_OBJECT);
		String listeId = "";
		synchronized(obj.getLock()){
			obj.resetFilters();
			obj.setFieldFilter(LOV_NAME_FIELD, listName);
			listeId = obj.search().get(0)[0];
		}
		obj = g.getTmpObject(FIELD_LIST_OBJECT);
		String listeIsoId = "";
		synchronized(obj.getLock()){
			obj.resetFilters();
			obj.setFieldFilter(LOV_NAME_FIELD, "LANG_ISO_639_1");
			listeIsoId = obj.search().get(0)[0];
		}
		obj = g.getTmpObject("FieldListCode");
		BusinessObjectTool objT = obj.getTool();
		synchronized(obj.getLock()){
			obj.resetFilters();
			obj.setFieldFilter(LOV_LIST_ID_FIELD, listeId);
			obj.setFieldFilter(LOV_CODE_FIELD, langIso);
			if(!obj.search().isEmpty()) {
				return;	
			}
			obj.resetFilters();
			obj.setFieldFilter(LOV_LIST_ID_FIELD, listeIsoId);
			obj.setFieldFilter(LOV_CODE_FIELD, langIso);
			List<String[]> row = obj.search();
			if(row.isEmpty()){
				return;
			}
			String id = row.get(0)[0];
			
			try {
				objT.selectForCopy(id,true);
				obj.setFieldValue(LOV_LIST_ID_FIELD, listeId);
				objT.validateAndCreate();
			} catch (GetException | CreateException | ValidateException e) {
				AppLog.error(e, g);
			}
		}
	}
	
	/**
	 * Returns the HTML code for a selector with options generated from a list of values.
	 * 
	 * @param listeName the name of the list of values
	 * @param dataName the name of the data, for process use
	 * @param g the Grant object
	 * @return the HTML code for the selector
	 */
	public static String getSelectorHtml(String listeName,String dataName,Grant g) {
		String template="<select class=\"form-control form-select enum-render js-focusable\" id=\"{{dataName}}\" name=\"{{dataName}}\">" + //
				"  {{#option}}" + //
				"  <option value=\"{{Val}}\">{{Display}}</option>" + //
				"  {{/option}}" + //
				"</select>";
		JSONArray option = new JSONArray();
		ListOfValues list = g.getListOfValues(listeName);
		for(String code :list.getCodes(listeName)){
			JSONObject obj = new JSONObject();
			
			obj.put("Val", code);
			obj.put("Display", list.getValue(listeName, code));

			option.put(obj);
		}
		
		JSONObject data = new JSONObject().put("dataName", dataName).put("option", option);
		return MustacheTool.apply(template, data);
	}
	
	/**
	 * Retrieves the ISO code for a given list name and code.
	 * Use to get the iso code equivalent of a deepl code language.
	 * @param listeName the name of the list
	 * @param code the code to search for
	 * @param g the Grant object
	 * @return the ISO code as a String
	 */
	public static String getISOCode(String listeName,String code,Grant g){
		ObjectDB obj = g.getTmpObject(FIELD_LIST_OBJECT);
		
		synchronized(obj.getLock()){
			obj.resetFilters();
			obj.setFieldFilter(LOV_NAME_FIELD, listeName);
			String lovId = obj.search().get(0)[0];
			
			ObjectDB objTrad = g.getTmpObject("FieldListCode");
			synchronized(objTrad.getLock()){
				objTrad.resetFilters();
				objTrad.setFieldFilter(LOV_LIST_ID_FIELD, lovId);
				objTrad.setFieldFilter(LOV_CODE_FIELD, code);
				return  objTrad.search().get(0)[objTrad.getFieldIndex("lov_label")];
			}
		}
	}
	public static String isoToDeepl(String iso) {
		return isoToDeeplMap.get(iso);
	}
}