package com.simplicite.workflows.Deepl;

import java.util.*;

import org.json.JSONObject;

import com.simplicite.bpm.*;
import com.simplicite.commons.Deepl.DeeplTool;
import com.simplicite.util.*;
import com.simplicite.util.exceptions.*;
import com.simplicite.util.tools.*;
import com.simplicite.webapp.ObjectContextWeb;

/**
 * Process DeeplObjectTra
 */
public class DeeplObjectTra extends Processus {
	private static final long serialVersionUID = 1L;
	private static final String TSL_VALUE_FIELD ="tsl_value";
	private static final String ACTIVITY_SELECT_TRANSLATION  ="DOT-0100";
	private static final String ACTIVITY_LANG  ="DOT-0200";
	//private static final String ACTIVITY_VALIDATE  ="DOT-0300";
	//private static final String ACTIVITY_TRANSLATE  ="DOT-0400";
	private static final String TSL_LANG_FIELD  ="tsl_lang";
	/**
	 * Returns the HTML representation of the language selection table.
	 *
	 * @param p the Processus object
	 * @param context the ActivityFile object
	 * @param ctx the ObjectContextWeb object
	 * @param g the Grant object
	 * @return the HTML representation of the language selection table
	 */
	public String  selectLang(Processus p, ActivityFile context, ObjectContextWeb ctx, Grant g){
		if(context.getStatus() != ActivityFile.STATE_RUNNING){
			return "";
		}
		String deeTrdLanguageHtml = DeeplTool.getSelectorHtml("DEEPL_LANG", "deeTrdLanguage",g);
		//html template of table from tsl_ValueHtml to deeTrdLanguageHtml
		return "<table class=\"table table-bordered table-hover table-sm\" id=\"table\" style=\"width:100%\">" + //
        		"  <thead>" + //
				"    <tr class=\"head\">" + //
				"      <th>Traduction</th>" + //
				"    </tr>" + //
				"  </thead>" + //
				"  <tbody>" + //
				"    <tr>" + //
				"      <td>"+deeTrdLanguageHtml+"</td>" + //
				"    </tr>" + //
				"  </tbody>" + //
				"</table>";

		
	}
	/**
	 * User validation of translate process.
	 * 
	 * @param p the processus object
	 * @param context the activity file context
	 * @param ctx the object context web
	 * @param g the grant object
	 * @return the validation result string
	 */
	public String  validate(Processus p, ActivityFile context, ObjectContextWeb ctx, Grant g) {
		if(context.getStatus() != ActivityFile.STATE_RUNNING){
			return "";
		}
		int count = charCount(p,g);
		return g.getLang().equals(Globals.LANG_FRENCH)?"Nombre estimée de caractères : "+count+"<br> <input type=\"checkbox\" id=\"update\" name=\"update\"/> Mettre à jour les traductions existantes":"Estimated number of characters :"+count+"<br> <input type=\"checkbox\" id=\"update\" name=\"update\"/> Update existing translations";
	}
	/**
	 * Calculates the total character count of the values in the specified process and grant.
	 * use to estimate api cost.
	 * @param p The processus object.
	 * @param g The grant object.
	 * @return The total character count.
	 */
	private int charCount(Processus p, Grant g){
		String[] tradIds = getContext(p.getActivity(ACTIVITY_SELECT_TRANSLATION)).getDataFile("Field", "row_id", true).getValues();
		//get instance of Translate object
		ObjectDB obj = g.getTmpObject("Translate");
			
		int count =0;
		synchronized(obj.getLock()){
			for (String id : tradIds) {
				obj.select(id);
				String value = obj.getFieldValue(TSL_VALUE_FIELD);
				if(!Tool.isEmpty(value)){
					count += value.length();
				}
			}
		}
		return count;
	}
	/**
	 * Translates the selected object text using Deepl API.
	 * 
	 * @param p The Processus object.
	 * @param context The ActivityFile object.
	 * @param ctx The ObjectContextWeb object.
	 * @param g The Grant object.
	 * @return The translated text.
	 */
	public String  translate(Processus p, ActivityFile context, ObjectContextWeb ctx, Grant g) {
		String[] tradIds = getContext(p.getActivity(ACTIVITY_SELECT_TRANSLATION)).getDataFile("Field", "row_id", true).getValues();
		String langTo = getContext(p.getActivity(ACTIVITY_LANG)).getDataValue("Data", "deeTrdLanguage");
		String langToIso= DeeplTool.getISOCode("DEEPL_LANG", langTo, g);
		boolean update = Tool.isEmpty(getContext(p.getActivity(ACTIVITY_LANG)).getDataValue("Data", "update"));
		// check if lang exist in usr lang
		DeeplTool.addLangList("LANG",langToIso,g);
		DeeplTool.addLangList("LANG_ALL",langToIso,g);
		ObjectDB obj = getGrant().getTmpObject("Translate");
		synchronized(obj.getLock()){
			BusinessObjectTool objT = obj.getTool();
			try {
				for (String id : tradIds) {
					
					objT.selectForCopy(id,true);
					String value = obj.getFieldValue(TSL_VALUE_FIELD);
					String fromLang = DeeplTool.isoToDeepl(obj.getFieldValue(TSL_LANG_FIELD));
					JSONObject filters = new JSONObject().put("tsl_object", obj.getFieldValue("tsl_object")).put(TSL_LANG_FIELD, langToIso);
					List<String[]> row = objT.search(filters);
					if(Tool.isEmpty(row)){
						obj.setFieldValue("tsl_lang", langToIso);
						ArrayList<String> sentences = new ArrayList<>();
						if(!Tool.isEmpty(value)){
							sentences.add(value);
							value = DeeplTool.callDeepl(sentences, fromLang, langTo, g);
						}
						obj.setFieldValue(TSL_VALUE_FIELD, value);
						objT.validateAndCreate();
					}else if(update){
						objT.select(row.get(0)[obj.getRowIdFieldIndex()]);
						ArrayList<String> sentences = new ArrayList<>();
						if(!Tool.isEmpty(value)){
							sentences.add(value);
							value = DeeplTool.callDeepl(sentences, fromLang, langTo, g);
						}
						obj.setFieldValue(TSL_VALUE_FIELD, value);
						objT.validateAndUpdate();
					}
				}
			} catch (GetException | HTTPException | CreateException | ValidateException | SearchException | UpdateException e) {
				AppLog.error(e, g);
				return g.getText("DEE_ERROR_TRANSLATE");
			}
			
		}
		return  g.getText("DEE_END_TRANSLATE");
	}
}