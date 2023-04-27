package oam.security.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import oam.security.service.MitreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("")
@Slf4j
public class SystemCtr
{

	@Autowired
	private ObjectMapper			objectMapper;
	@Autowired
	private MitreService mitreService;

	@PostMapping(value = "/hooks/Elastalert")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void ReceiveWebhook(@RequestBody final JsonNode json) throws Exception
	{
		log.debug("\t Receive webhook from Affirmed ElasticSearch: \n{}", json.toPrettyString());
	}

	@PostMapping(value = "/v1/test2")
	@ResponseStatus(HttpStatus.OK)
	public JsonNode test2(@RequestBody final JsonNode json) throws Exception
	{

		// final JsonNode fight = jsonService.getJsonNodeFromClasspathForYaml("mitre/fight.yaml");
		final String id = mitreService.getTacticIdByName("Reconnaissance");
		log.debug("Input Json={}", mitreService.getMitigationsByTechniqueId("FGT1014").toPrettyString());

		return mitreService.getMitigationsByTechniqueId("FGT1014");
		// log.debug("Input Json={}", json.toPrettyString());
		// jsonSchemaService.validate("schema.yaml", json);

		// json valid檢查格式
		// final JsonNode schemaNode = baseJsonSchemaValidator.getJsonNodeFromClasspath("json-schema/schema.yaml");
		// final JsonSchema schema = baseJsonSchemaValidator.getJsonSchemaFromJsonNodeAutomaticVersion(schemaNode);

		// final ObjectMapper yamlObjMapper = new ObjectMapper(new YAMLFactory());
		// final JsonSchemaFactory factory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)).objectMapper(yamlObjMapper)
		// .build(); /* Using draft-07. You can choose anyother draft. */
		// final InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("json-schema/schema.yaml");
		// final JsonSchema schema = factory.getSchema(is);
	}
}