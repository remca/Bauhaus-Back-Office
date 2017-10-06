package fr.insee.rmes.persistance.service.sesame.export;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONObject;

import fr.insee.rmes.config.Config;
import fr.insee.rmes.persistance.disseminationStatus.DisseminationStatus;
import fr.insee.rmes.persistance.service.sesame.collections.CollectionsQueries;
import fr.insee.rmes.persistance.service.sesame.concepts.ConceptsQueries;
import fr.insee.rmes.persistance.service.sesame.utils.RepositoryGestion;

public class Export {

	public JSONObject getConceptData(String id) {
		JSONObject data = new JSONObject();
		JSONObject general = RepositoryGestion.getResponseAsObject(ConceptsQueries.conceptQuery(id));
		if (general.getString("altLabelLg1").equals("")) general.remove("altLabelLg1");
		if (general.getString("altLabelLg2").equals("")) general.remove("altLabelLg2");
		data.put("prefLabelLg1", general.getString("prefLabelLg1"));
		if (general.has("prefLabelLg2"))
			data.put("prefLabelLg2", general.getString("prefLabelLg2"));
		data.put("general", editGeneral(general));
		JSONArray links = RepositoryGestion.getResponseAsArray(ConceptsQueries.conceptLinks(id));
		data.put("links", editLinks(links));
		JSONObject notes = RepositoryGestion.getResponseAsObject(
				ConceptsQueries.conceptNotesQuery(id, Integer.parseInt(general.getString("conceptVersion"))));
		editNotes(notes, data);
		return data;
	}

	public JSONObject getCollectionData(String id) {
		JSONObject data = new JSONObject();
		JSONObject json = RepositoryGestion.getResponseAsObject(CollectionsQueries.collectionQuery(id));
		data.put("prefLabelLg1", json.getString("prefLabelLg1"));
		if (json.has("prefLabelLg2"))
			data.put("prefLabelLg2", json.getString("prefLabelLg2"));
		data.put("general", editGeneral(json));
		if (json.has("descriptionLg1"))
			data.put("descriptionLg1", json.getString("descriptionLg1") + "<p></p>");
		if (json.has("descriptionLg2"))
			data.put("descriptionLg2", json.getString("descriptionLg2") + "<p></p>");
		JSONArray members = RepositoryGestion.getResponseAsArray(CollectionsQueries.collectionMembersQuery(id));
		String membersLg1 = extractMembers(members, "prefLabelLg1");
		if (!membersLg1.equals("")) {
			data.put("membersLg1", membersLg1);
			data.put("membersLg2", extractMembers(members, "prefLabelLg2"));
		}
		return data;
	}

	private String editGeneral(JSONObject json) {
		StringBuilder xhtml = new StringBuilder("<ul>");
		if (json.has("altLabelLg1"))
			xhtml.append("<li>Libellé alternatif (" + Config.LG1 + ") : " + json.getString("altLabelLg1") + "</li>");
		if (json.has("altLabelLg2"))
			xhtml.append("<li>Libellé alternatif (" + Config.LG2 + ") : " + json.getString("altLabelLg2") + "</li>");
		if (json.has("created"))
			xhtml.append("<li>Date de création : " + toDate(json.getString("created")) + "</li>");
		if (json.has("modified"))
			xhtml.append("<li>Date de modification : " + toDate(json.getString("modified")) + "</li>");
		if (json.has("valid"))
			xhtml.append("<li>Date de fin de validité : " + toDate(json.getString("valid")) + "</li>");
		if (json.has("disseminationStatus"))
			xhtml.append("<li>Statut de diffusion : " + toLabel(json.getString("disseminationStatus")) + "</li>");
		if (json.has("additionalMaterial"))
			xhtml.append("<li>Document lié : " + json.getString("additionalMaterial") + "</li>");
		if (json.has("creator"))
			xhtml.append("<li>Timbre propriétaire : " + json.getString("creator") + "</li>");
		if (json.has("contributor"))
			xhtml.append("<li>Timbre gestionnaire : " + json.getString("contributor") + "</li>");
		if (json.has("isValidated"))
			xhtml.append("<li>Statut de validation : " + json.getString("isValidated") + "</li>");
		xhtml.append("</ul><p></p>");
		return xhtml.toString();
	}

	private String extractMembers(JSONArray array, String attr) {
		TreeSet<String> list = new TreeSet<String>();
		for (int i = 0; i < array.length(); i++) {
			JSONObject jsonO = (JSONObject) array.get(i);
			list.add(jsonO.getString(attr));
		}
		if (list.isEmpty())
			return "";
		StringBuilder xhtml = new StringBuilder("<ul>");
		for (String member : list) {
			xhtml.append("<li>" + member + "</li>");
			;
		}
		xhtml.append("</ul><p></p>");
		return xhtml.toString();
	}

	private String editLinks(JSONArray array) {
		TreeSet<String> listParents = new TreeSet<String>();
		TreeSet<String> listEnfants = new TreeSet<String>();
		TreeSet<String> listReferences = new TreeSet<String>();
		TreeSet<String> listSucceed = new TreeSet<String>();
		TreeSet<String> listReplaces = new TreeSet<String>();
		for (int i = 0; i < array.length(); i++) {
			JSONObject jsonO = (JSONObject) array.get(i);
			String typeOfLink = jsonO.getString("typeOfLink");
			if (typeOfLink.equals("broader"))
				listParents.add(jsonO.getString("prefLabelLg1"));
			if (typeOfLink.equals("narrower"))
				listEnfants.add(jsonO.getString("prefLabelLg1"));
			if (typeOfLink.equals("references"))
				listReferences.add(jsonO.getString("prefLabelLg1"));
			if (typeOfLink.equals("succeed"))
				listSucceed.add(jsonO.getString("prefLabelLg1"));
			if (typeOfLink.equals("related"))
				listReplaces.add(jsonO.getString("prefLabelLg1"));
		}
		if (listParents.isEmpty() && listEnfants.isEmpty() && listReferences.isEmpty() && listSucceed.isEmpty()
				&& listReplaces.isEmpty())
			return "";
		StringBuilder xhtml = new StringBuilder("");
		linksByType(xhtml, listParents, "Concept parent");
		linksByType(xhtml, listEnfants, "Concept enfant");
		linksByType(xhtml, listReferences, "Concept référencé");
		linksByType(xhtml, listSucceed, "Succède à");
		linksByType(xhtml, listReplaces, "Concept lié");
		xhtml.append("<p></p>");
		return xhtml.toString();
	}

	private StringBuilder linksByType(StringBuilder xhtml, TreeSet<String> list, String title) {
		if (list.isEmpty())
			return xhtml;
		xhtml.append("<U>" + title + " :</U>");
		xhtml.append("<ul>");
		for (String item : list) {
			xhtml.append("<li>" + item + "</li>");
			;
		}
		xhtml.append("</ul><p></p>");
		return xhtml;
	}

	private void editNotes(JSONObject notes, JSONObject data) {
		List<String> noteTypes = Arrays.asList("scopeNoteLg1", "scopeNoteLg2", "definitionLg1", "definitionLg2",
				"editorialNoteLg1", "editorialNoteLg2");
		noteTypes.forEach(noteType -> {
			if (notes.has(noteType))
				data.put(noteType, notes.getString(noteType) + "<p></p>");
		});
	}

	private String toLabel(String dsURL) {
		return DisseminationStatus.getEnumLabel(dsURL);
	}
	
	private String toDate(String dateTime) {
		String dateString = dateTime.substring(8, 10) + "/" + dateTime.substring(5, 7) + "/" + dateTime.substring(0, 4);
		return dateString;
	}

}
