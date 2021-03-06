package fr.insee.rmes.persistance.mailSender;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openrdf.model.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import fr.insee.rmes.config.Config;
import fr.insee.rmes.config.auth.security.restrictions.StampsRestrictionsService;
import fr.insee.rmes.exceptions.RmesUnauthorizedException;
import fr.insee.rmes.persistance.export.Jasper;
import fr.insee.rmes.persistance.mailSender.rmes.MessageTemplate;
import fr.insee.rmes.persistance.mailSender.rmes.NameValuePairType;
import fr.insee.rmes.persistance.mailSender.rmes.Recipient;
import fr.insee.rmes.persistance.mailSender.rmes.SendRequest;
import fr.insee.rmes.persistance.mailSender.rmes.SendRequest.Recipients;
import fr.insee.rmes.persistance.service.sesame.concepts.concepts.ConceptsExportBuilder;
import fr.insee.rmes.persistance.service.sesame.utils.SesameUtils;
import fr.insee.rmes.persistance.mailSender.rmes.ServiceConfiguration;

@Service
public class RmesMailSenderImpl implements MailSenderContract {
	
	@Autowired
	ConceptsExportBuilder conceptsExport;
	
	@Autowired
	Jasper jasper;
	
	@Autowired
	StampsRestrictionsService stampsRestrictionsService;
		
	public boolean sendMailConcept(String id, String body) throws Exception {
		URI conceptURI = SesameUtils.conceptIRI(id);
		if (!stampsRestrictionsService.isConceptOrCollectionOwner(conceptURI))
			throw new RmesUnauthorizedException();
		Mail mail = prepareMail(body);
		JSONObject json = conceptsExport.getConceptData(id);
		InputStream is = jasper.exportConcept(json, "Mail");
		return sendMail(mail, is, json);
	}
	
	public boolean sendMailCollection(String id, String body) throws Exception {
		URI collectionURI = SesameUtils.collectionIRI(id);
		if (!stampsRestrictionsService.isConceptOrCollectionOwner(collectionURI))
			throw new RmesUnauthorizedException();
		Mail mail = prepareMail(body);
		JSONObject json = conceptsExport.getCollectionData(id);
		InputStream is = jasper.exportCollection(json, "Mail");
		return sendMail(mail, is, json);
	}
		
	private boolean sendMail(Mail mail, InputStream is, JSONObject json) {
		
		String fileName = json.getString("prefLabelLg1");
		fileName = Normalizer.normalize(fileName.toLowerCase()
				.replaceAll(" ", "-"), Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "") + ".odt";
		
		MessageTemplate messagetemplate = new MessageTemplate();

		NameValuePairType nameValuePairType = new NameValuePairType();
		nameValuePairType.setName("Content-Type");
		nameValuePairType.setValue("text/html; charset=UTF-8");
		messagetemplate.getHeader().add(nameValuePairType);
		messagetemplate.setSender(mail.getSender());
		messagetemplate.setSubject(mail.getObject());
		messagetemplate.setContent(mail.getMessage());

		// PJ
		List<String> attachments = new ArrayList<String>();
		attachments.add(fileName);
		
		// création des destinataires
		Recipient destinataire1 = new Recipient();
		destinataire1.setAddress(mail.getRecipient());
		destinataire1.getAttachments().add(fileName);
		Recipients destinataires = new Recipients();
		destinataires.getRecipient().add(destinataire1);

		// préparation de la requête à envoyer
		SendRequest request = new SendRequest();
		request.setMessageTemplate(messagetemplate);
		request.setRecipients(destinataires);
		
		// Contenu html
		NameValuePairType nameValuePairTypeSmtpFrom = new NameValuePairType();
		nameValuePairTypeSmtpFrom.setName("mail.smtp.from");
		nameValuePairTypeSmtpFrom.setValue(mail.getSender());
		ServiceConfiguration config = new ServiceConfiguration();
		config.getSMTPProperties().add(nameValuePairTypeSmtpFrom);
		request.setServiceConfiguration(config);

		// création d'un client authentifié pour SPOC	
		HttpAuthenticationFeature authentificationFeature = HttpAuthenticationFeature
				.basic(Config.SPOC_USER, Config.SPOC_PASSWORD);
		Client client = ClientBuilder.newClient().register(
				authentificationFeature);
			
		// Multipart
		
		client.register(MultiPartFeature.class);
		
		FormDataMultiPart mp = new FormDataMultiPart();
		mp.bodyPart(new FormDataBodyPart(FormDataContentDisposition.name("request").build(),request,MediaType.APPLICATION_XML_TYPE));
		final StreamDataBodyPart bodyPart = new StreamDataBodyPart("attachments", is, fileName);
		mp.bodyPart(bodyPart);
				
		String result = client.target(Config.SPOC_SERVICE_URL)
				.request()
				.post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE),String.class); 	
		return isMailSent(result);
	}
	
	private Mail prepareMail(String body) {
		ObjectMapper mapper = new ObjectMapper();
		Mail mail = new Mail();
		try {
			mail = mapper.readValue(body, Mail.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return mail;
	}
	
	private boolean isMailSent(String result) {
		JSONObject response = new JSONObject(result);
		if (response.get("ReportItem").toString() != null) {
			JSONArray reportItem = (JSONArray) response.get("ReportItem");
			JSONObject firstReportItem = (JSONObject) reportItem.get(0);
			boolean sent = firstReportItem.getBoolean("sent");
			return sent;
		}
		else return false;
	}
}
