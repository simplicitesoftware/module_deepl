package com.simplicite.workflows.Deepl;

import java.util.*;

import org.json.JSONException;
import org.json.JSONObject;

import com.simplicite.bpm.*;
import com.simplicite.commons.Deepl.DeeplTool;
import com.simplicite.util.*;
import com.simplicite.util.exceptions.*;
import com.simplicite.util.tools.*;
import com.simplicite.webapp.ObjectContextWeb;



/**
 * Process DeeplModuleTrad
 */
public class DeeplModuleTrad extends Processus {
	private static final long serialVersionUID = 1L;
	private static final String ROW_MODULE_ID_FIELD  ="row_module_id";
	private static final String TSL_LANG_FIELD ="tsl_lang";
	private static final String TSL_VALUE_FIELD ="tsl_value";
	private static final String TSL_PLURAL_VALUE_FIELD ="tsl_plural_value";
	private static final String LOV_LANG_FIELD ="lov_lang";
	private static final String LOV_VALUE_FIELD ="lov_value";
	private static final String LOV_CODE_ID_FIELD ="lov_code_id";
	private static final String ACTIVITY_SELECT_MODULE ="DMT_0100";
	private static final String ACTIVITY_SELECT_LANG ="DMT_0200";
	private static final String ACTIVITY_VALIDATION ="DMT_0250";
	//private static final String ACTIVITY_TRADUCTION_PROCESS ="DMT_0300";
	/**
	 * User validation of translate process.
	 * 
	 * @param p the processus object
	 * @param context the activity file context
	 * @param ctx the object context web
	 * @param g the grant object
	 * @return the validation result string
	 */
	public String validation(Processus p, ActivityFile context, ObjectContextWeb ctx, Grant g){
		if(context.getStatus() != ActivityFile.STATE_RUNNING){
			return "";
		}
		int count = charCount(p,g);

		return g.getLang().equals(Globals.LANG_FRENCH)?"Nombre estimée de caractères : "+count+"<br> <input type=\"checkbox\" id=\"update\" name=\"update\"/> Mettre à jour les traductions existantes":"Estimated number of characters : "+count+"<br> <input type=\"checkbox\" id=\"update\" name=\"update\"/> Update existing translations";
	}
	/**
	 * Returns the HTML representation of a table containing language selectors for translation.
	 * The table consists of two columns: "Langue" and "Traduction".
	 * The "Langue" column displays the HTML representation of the tsl_valueHtml selector.
	 * The "Traduction" column displays the HTML representation of the deeTrdLanguageHtml selector.
	 * 
	 * @param p the Processus object
	 * @param context the ActivityFile object
	 * @param ctx the ObjectContextWeb object
	 * @param g the Grant object
	 * @return the HTML representation of the language selector table
	 */
	public String selectLang(Processus p, ActivityFile context, ObjectContextWeb ctx, Grant g){
		if(context.getStatus() != ActivityFile.STATE_RUNNING){
			return "";
		}
		String deeTrdLanguageHtml = DeeplTool.getSelectorHtml("DEEPL_LANG", "deeTrdLanguage",g);
		String tslValueHtml = DeeplTool.getSelectorHtml("LANG_ALL", TSL_LANG_FIELD,g);
		//html template of table from tsl_ValueHtml to deeTrdLanguageHtml
		String language = g.getLang().equals(Globals.LANG_FRENCH)?"Langue":"Language";
		String traduction = g.getLang().equals(Globals.LANG_FRENCH)?"Traduction":"Translation";
		return "<table class=\"table table-bordered table-hover table-sm\" id=\"table\" style=\"width:100%\">" + //
        		"  <thead>" + //
				"    <tr class=\"head\">" + //
				"      <th>"+language+"</th>" + //
				"      <th>"+traduction+"</th>" + //
				"    </tr>" + //
				"  </thead>" + //
				"  <tbody>" + //
				"    <tr>" + //
				"      <td>"+tslValueHtml+"</td>" + //
				"      <td>"+deeTrdLanguageHtml+"</td>" + //
				"    </tr>" + //
				"  </tbody>" + //
				"</table>";
	}
	/**
	 * This method performs the translation process.
	 * 
	 * @param p The process object.
	 * @param context The activity file context.
	 * @param ctx The web object context.
	 * @param g The grant object.
	 * @return The result of the translation process.
	 */
	public String translationProcess(Processus p, ActivityFile context, ObjectContextWeb ctx, Grant g){
		if(context.getStatus() != ActivityFile.STATE_RUNNING){
			return "";
		}
		String update = getContext(p.getActivity(ACTIVITY_VALIDATION)).getDataValue("Data", "update");
		return translationProcess(p, Tool.isEmpty(update), g);
	}
	

	/**
	 * performs the translation process.
	 * 
	 * @param p
	 * @param update
	 * @param g
	 * @return
	 */
	private String translationProcess(Processus p,boolean update, Grant g){
		String langfrom = getContext(p.getActivity(ACTIVITY_SELECT_LANG)).getDataValue("Data", TSL_LANG_FIELD);
		String langTo = getContext(p.getActivity(ACTIVITY_SELECT_LANG)).getDataValue("Data", "deeTrdLanguage");
		String langToIso=  DeeplTool.getISOCode("DEEPL_LANG", langTo, g);
		String moduleID = getContext(p.getActivity(ACTIVITY_SELECT_MODULE)).getDataValue("Field", "row_id");
		// check if lang exist in usr lang

		DeeplTool.addLangList("LANG",langToIso,g);
		DeeplTool.addLangList("LANG_ALL",langToIso,g);
		//get instance of Translate object
		try{
			translationObject(g, moduleID, langfrom, langTo, langToIso, update);
			translationList(g, moduleID, langfrom, langTo, langToIso, update);
			translationStaticText(g, moduleID, langfrom, langTo, langToIso, update);
		} catch ( UpdateException | CreateException | JSONException | ValidateException | GetException | HTTPException e) {
			AppLog.error(e, g);
			
			return g.getText("DEE_ERROR_TRANSLATE");
		}
		//Translate List
		return  g.getText("DEE_END_TRANSLATE");
	}
	
	private String translationObject(Grant g,String moduleID,String langfrom, String langTo, String langToIso, boolean update) throws UpdateException, CreateException, GetException, HTTPException, ValidateException, JSONException{
		ObjectDB obj = g.getTmpObject("Translate");
		BusinessObjectTool objT = obj.getTool();		
		synchronized(obj.getLock()){
			obj.resetFilters();
			obj.setFieldFilter(ROW_MODULE_ID_FIELD, moduleID);
			obj.setFieldFilter(TSL_LANG_FIELD, langfrom);
			for(String[] row : obj.search()){
				String value = row[obj.getFieldIndex(TSL_VALUE_FIELD)];
				String valuePlural = row[obj.getFieldIndex(TSL_PLURAL_VALUE_FIELD)];
				JSONObject json = new JSONObject().put("tsl_object", row[obj.getFieldIndex("tsl_object")]).put(TSL_LANG_FIELD, langToIso).put(ROW_MODULE_ID_FIELD, row[obj.getFieldIndex(ROW_MODULE_ID_FIELD)]);
				if(objT.selectForCreateOrUpdate(json)){
					if((update || Tool.isEmpty(obj.getFieldValue(TSL_VALUE_FIELD)))){
						tradObjectField(obj,TSL_VALUE_FIELD, langTo ,value, g);
					}
					if((update || Tool.isEmpty(obj.getFieldValue(TSL_PLURAL_VALUE_FIELD)))){
						tradObjectField(obj,TSL_PLURAL_VALUE_FIELD, langTo, valuePlural, g);
					}
					objT.validateAndUpdate();
				}else{//if create
					obj.setValuesFromJSONObject(json, false, false);
					tradObjectField(obj,TSL_VALUE_FIELD, langTo, value, g);
					tradObjectField(obj,TSL_PLURAL_VALUE_FIELD, langTo, valuePlural, g);
					objT.validateAndCreate();
				}
			}
		}
		return "";
	}
	private void translationStaticText(Grant g,String moduleID,String langfrom, String langTo, String langToIso, boolean update) throws UpdateException, CreateException, GetException, HTTPException, ValidateException{
		ObjectDB obj = g.getTmpObject("ListOfValue");
		BusinessObjectTool objT = obj.getTool();		
		synchronized(obj.getLock()){
			obj.resetFilters();
			obj.setFieldFilter(ROW_MODULE_ID_FIELD, moduleID);
			obj.setFieldFilter(LOV_LANG_FIELD, langfrom);
			for(String[] row : obj.search()){
				String value = row[obj.getFieldIndex(LOV_VALUE_FIELD)];
				JSONObject json = new JSONObject()
								.put("lov_name", row[obj.getFieldIndex("lov_name")])
								.put("lov_code", row[obj.getFieldIndex("lov_code")])
								.put(LOV_LANG_FIELD, langToIso)
								.put(ROW_MODULE_ID_FIELD, row[obj.getFieldIndex(ROW_MODULE_ID_FIELD)]);
				if(objT.selectForCreateOrUpdate(json)){
					if(!Tool.isEmpty(value) && (update || Tool.isEmpty(obj.getFieldValue(LOV_VALUE_FIELD)))){
						
						List<String> sentences = new ArrayList<>();
						sentences.add(value);
						value = DeeplTool.callDeepl(sentences, langTo, g);
						obj.setFieldValue(LOV_VALUE_FIELD, value);
						
					}
					objT.validateAndUpdate();
				}else{//if create
					obj.setValuesFromJSONObject(json, false, false);
					if(!Tool.isEmpty(value)){
						List<String> sentences = new ArrayList<>();
						sentences.add(value);
						value = DeeplTool.callDeepl(sentences, langTo, g);
						obj.setFieldValue(LOV_VALUE_FIELD, value);
					}
					objT.validateAndCreate();
				}
			}
		}
	}
	private void tradObjectField(ObjectDB obj,String field, String langTo ,String value, Grant g) throws HTTPException{
		
		if(!Tool.isEmpty(value)){
			List<String> sentences = new ArrayList<>();
			sentences.add(value);
			obj.setFieldValue(field, DeeplTool.callDeepl(sentences, langTo, g));
		}
	}
	
	private String translationList(Grant g,String moduleID,String langfrom, String langTo, String langToIso, boolean update) throws UpdateException, CreateException, GetException, JSONException, HTTPException, ValidateException{
		ObjectDB obj = g.getTmpObject("FieldListValue");
		BusinessObjectTool objT = obj.getTool();
		synchronized(obj.getLock()){
			obj.resetFilters();
			obj.setFieldFilter(ROW_MODULE_ID_FIELD, moduleID);
			obj.setFieldFilter(LOV_LANG_FIELD, langfrom);
			for(String[] row : obj.search()){
				String value = row[obj.getFieldIndex(LOV_VALUE_FIELD)];	
				if(objT.selectForCreateOrUpdate(new JSONObject().put(LOV_CODE_ID_FIELD, row[obj.getFieldIndex(LOV_CODE_ID_FIELD)]).put(LOV_LANG_FIELD, langToIso))){
					if(update){
						List<String> sentences = new ArrayList<>();
						sentences.add(value);
						value = DeeplTool.callDeepl(sentences, langTo, g);
						obj.setFieldValue(LOV_VALUE_FIELD, value);
						objT.validateAndUpdate();
					}
					
				}else{//if create
					List<String> sentences = new ArrayList<>();
					sentences.add(value);
					value = DeeplTool.callDeepl(sentences, langTo, g);
					obj.setFieldValue(LOV_CODE_ID_FIELD, row[obj.getFieldIndex(LOV_CODE_ID_FIELD)]);
					obj.setFieldValue(LOV_LANG_FIELD, langToIso);
					obj.setFieldValue(ROW_MODULE_ID_FIELD, row[obj.getFieldIndex(ROW_MODULE_ID_FIELD)]);
					obj.setFieldValue(LOV_VALUE_FIELD, value);
					objT.validateAndCreate();
				}
				
			}
		}
		return "";
	}

	
	/**
	 * Calculates the total character count  for a given module and language.
	 * 
	 * @param p The Processus object.
	 * @param g The Grant object.
	 * @return The total character count.
	 */
	private int charCount(Processus p, Grant g){
		String langfrom = getContext(p.getActivity(ACTIVITY_SELECT_LANG)).getDataValue("Data", TSL_LANG_FIELD);
		String moduleID = getContext(p.getActivity(ACTIVITY_SELECT_MODULE)).getDataValue("Field", "row_id");
		
		//get instance of Translate object
		ObjectDB obj = g.getTmpObject("Translate");
		int count =0;
		synchronized(obj.getLock()){
			
			obj.resetFilters();
			obj.setFieldFilter(ROW_MODULE_ID_FIELD, moduleID);
			obj.setFieldFilter(TSL_LANG_FIELD, langfrom);
			for(String[] row : obj.search()){
				String value = row[obj.getFieldIndex(TSL_VALUE_FIELD)];
				count += value.length();
				value = row[obj.getFieldIndex(TSL_PLURAL_VALUE_FIELD)];
				count += value.length();
			}
		}
		obj = g.getTmpObject("FieldListValue");
		synchronized(obj.getLock()){
			
			obj.resetFilters();
			obj.setFieldFilter(ROW_MODULE_ID_FIELD, moduleID);
			obj.setFieldFilter(LOV_LANG_FIELD, langfrom);
			for(String[] row : obj.search()){
				String value = row[obj.getFieldIndex(LOV_VALUE_FIELD)];
				count += value.length();
			}
		}

		obj = g.getTmpObject("ListOfValue");
		synchronized(obj.getLock()){
			
			obj.resetFilters();
			obj.setFieldFilter(ROW_MODULE_ID_FIELD, moduleID);
			obj.setFieldFilter(LOV_LANG_FIELD, langfrom);
			for(String[] row : obj.search()){
				String value = row[obj.getFieldIndex(LOV_VALUE_FIELD)];
				count += value.length();
			}
		}
		return count;
	}

 
}