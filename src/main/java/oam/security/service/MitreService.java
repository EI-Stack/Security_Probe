package oam.security.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.extern.slf4j.Slf4j;
import oam.security.capability.jsonvalid.JsonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@Slf4j
public class MitreService
{
	private JsonNode		fight;
	private ArrayNode       dataSources;
	private ArrayNode		tactics;
	private ArrayNode		techniques;
	private ArrayNode		mitigations;

	@Autowired
	private JsonService 	jsonService;
	@Autowired
	private ObjectMapper	objectMapper;

	@PostConstruct
	public void init()
	{
		try
		{
			fight = jsonService.getJsonNodeFromClasspathForYaml("mitre/fight.yaml");
		} catch (final IOException e)
		{
			log.error("Can not read file mitre/fight.yaml");
		}
		dataSources = (ArrayNode) fight.path("data sources");
		tactics = (ArrayNode) fight.path("tactics");
		techniques = (ArrayNode) fight.path("techniques");
		mitigations = (ArrayNode) fight.path("mitigations");
	}

//====================================Matrices============================================================
	public JsonNode getAllMatrices(){
		return fight;
	}

	public JsonNode getAllDataSources(){
		return dataSources;
	}

	public JsonNode getName(){
		return fight.path("name");
	}

//===================================Tactics=============================================================
	public ArrayNode getAllTactics(){
		return tactics;
	}

	public String getTacticNameById(final String tacticId)
	{
		String tacticName = null;
		for (final JsonNode tactic : tactics)
		{
			if (tactic.path("id").asText().equalsIgnoreCase(tacticId)) {
				tacticName = tactic.path("name").asText();
				break;
			}
		}
		return tacticName;
	}

	public String getTacticIdByName(final String tacticName)
	{
		String tacticId = null;
		for (final JsonNode tactic : tactics)
		{
			if (tactic.path("name").asText().equalsIgnoreCase(tacticName)) {
				tacticId = tactic.path("id").asText();
				break;
			}
		}
		return tacticId;
	}

	public JsonNode getTacticsByMatrix(){
		//回傳全部name.
		ArrayNode resultTactics = JsonNodeFactory.instance.arrayNode();
		for (JsonNode tactic : tactics) {
			resultTactics.add(tactic.path("name").asText());
		}
		return resultTactics;
	}

//===================================Techniques==========================================================
	public ArrayNode getAllTechniques(){
		return techniques;
	}

	public ArrayNode getAllParentTechniques(){
		final ArrayNode resultSubTechniques = JsonNodeFactory.instance.arrayNode();

		for (JsonNode technique : techniques) {
			//subTechniques會多一個欄位叫"subTechnique-of" 可以用這個去判斷是否為subTechniques.
			if (technique.path("subtechnique-of").asText().isEmpty()){
				resultSubTechniques.add(technique);
			}
		}
		return resultSubTechniques;
	}

	public ArrayNode getAllTechniquesMitigatedByAllMitigations(){
		final ArrayNode resultTechniques = JsonNodeFactory.instance.arrayNode();

		for (JsonNode technique : techniques) {
			if (!technique.path("mitigations").isEmpty()){
				resultTechniques.add(technique);
			}
		}
		return resultTechniques;
	}

	public ArrayNode getTechniquesByTacticId(final String tacticId) throws IOException
	{
		final ArrayNode resultTactics = JsonNodeFactory.instance.arrayNode();

		for (final JsonNode technique : techniques)
		{
			final Set<String> tacticIdSet = objectMapper.readValue(technique.path("tactics").traverse(), new TypeReference<LinkedHashSet<String>>()
			{});

			if (tacticIdSet.contains(tacticId)) resultTactics.add(technique);
		}
		return resultTactics;
	}

	public ArrayNode getTechniqueMitigatedByMitigation(String mitigationId){
		final ArrayNode resultTechniques = JsonNodeFactory.instance.arrayNode();
		final ArrayNode resultMitigations = JsonNodeFactory.instance.arrayNode();
		for (JsonNode mitigation : mitigations) {
			if (mitigation.path("id").asText().equalsIgnoreCase(mitigationId)){
				resultMitigations.add(mitigation.path("techniques"));
			}
		}

		for (JsonNode technique : techniques) {
			if (resultMitigations.toString().contains(technique.path("id").asText())){
				resultTechniques.add(technique);
			}
		}
		return resultTechniques;
	}


	public ArrayNode getParentTechniqueOfSubTechnique(final String subTechniqueId) throws IOException
	{
		final ArrayNode resultTechniques = JsonNodeFactory.instance.arrayNode();

		String techniqueId = null;
		for (JsonNode technique : techniques) {
			if (technique.path("id").asText().equalsIgnoreCase(subTechniqueId)){
				techniqueId = technique.path("subtechnique-of").asText();
			}

			if (technique.path("id").asText().equalsIgnoreCase(techniqueId)){
				resultTechniques.add(technique);
			}
		}

		return resultTechniques;
	}

	//===================================SubTechniques=======================================================
	public ArrayNode getAllSubTechniques(){
		final ArrayNode resultSubTechniques = JsonNodeFactory.instance.arrayNode();

		for (JsonNode technique : techniques) {
			//subTechniques會多一個欄位叫"subTechnique-of" 可以用這個去判斷是否為subTechniques.
			if (!technique.path("subtechnique-of").asText().isEmpty()){
				resultSubTechniques.add(technique);
			}
		}
		return resultSubTechniques;
	}

	public ArrayNode getSubTechniquesOfTechnique(String techniqueId){
		final ArrayNode resultSubTechniques = JsonNodeFactory.instance.arrayNode();

		for (JsonNode technique : techniques) {
			//subTechniques會多一個欄位叫"subTechnique-of" 可以比對techniqueId.
			if (technique.path("subtechnique-of").asText().equalsIgnoreCase(techniqueId)){
				resultSubTechniques.add(technique);
			}
		}
		return resultSubTechniques;
	}




//===================================Mitigations=========================================================
	public ArrayNode getAllMitigations(){
		return mitigations;
	}

	public ArrayNode getAllMitigationsMitigatingAllTechniques()
	{
		ArrayNode resultMitigations = JsonNodeFactory.instance.arrayNode();
		for (final JsonNode mitigation : mitigations)
		{
			if (!mitigation.path("techniques").isEmpty()){
				resultMitigations.add(mitigation);
			}
		}
		return resultMitigations;
	}

	public ArrayNode getMitigationsMitigatingTechnique(final String techniqueId)
	{
		ArrayNode resultMitigations = JsonNodeFactory.instance.arrayNode();
		for (final JsonNode mitigation : mitigations)
		{
			if (mitigation.path("techniques").toString().contains(techniqueId)) {
				resultMitigations.add(mitigation);
			}
		}
		return resultMitigations;
	}


	public ArrayNode getMitigationsByTechniqueId(final String techniqueId)
	{
		ArrayNode resultMitigations = JsonNodeFactory.instance.arrayNode();
		for (final JsonNode technique : techniques)
		{
			if (technique.path("id").asText().equalsIgnoreCase(techniqueId))
			{
				resultMitigations = (ArrayNode) technique.path("mitigations");
				break;
			}
		}
		return resultMitigations;
	}

//===================================Objects=============================================================
	public ArrayNode getObjectByStixId(final String id)
	{
		ArrayNode resultObject = getResultObjects("id", id);
		return resultObject;
	}

	public ArrayNode getObjectByAttackId(final String id)
	{
		ArrayNode resultObject = getResultObjects("typecode", id);
		return resultObject;
	}


	public ArrayNode getObjectByName(final String name)
	{
		ArrayNode resultObject = getResultObjects("name", name);
		return resultObject;
	}


	public ArrayNode getObjectByType(final String type)
	{
		ArrayNode resultObject = getResultObjects("object-type", type);
		return resultObject;
	}



	public ArrayNode getResultObjects(String source , String params){
		ArrayNode resultObject = JsonNodeFactory.instance.arrayNode();
		//source 會有technique, data source, mitigation, tactic，要回傳全部的內容.

		//tactic
		for (JsonNode tactic : tactics) {
			if (tactic.path(source).asText().equalsIgnoreCase(params))
			{
				resultObject.add(tactic);
			}
		}

		//technique
		for (JsonNode technique : techniques) {
			if (technique.path(source).asText().equalsIgnoreCase(params))
			{
				resultObject.add(technique);
			}
		}

		//data source
		for (JsonNode dataSource : dataSources) {
			if (dataSource.path(source).asText().equalsIgnoreCase(params))
			{
				resultObject.add(dataSource);
			}
		}

		//mitigation
		for (JsonNode mitigation : mitigations) {
			if (mitigation.path(source).asText().equalsIgnoreCase(params))
			{
				resultObject.add(mitigation);
			}
		}

		return resultObject;
	}

}