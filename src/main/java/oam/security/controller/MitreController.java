package oam.security.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import oam.security.service.MitreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/v1")
@Slf4j
public class MitreController {
    @Autowired
    private MitreService mitreService;

//====================================Matrices============================================================
    /**
     * FiGHT沒有單獨的matrices，直接抓取整個FiGHT內容.
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllMatrices")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllMatrices() throws Exception
    {
        return mitreService.getAllMatrices();
    }

    /**
     * 抓取全部dataSources.
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllDataSources")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllDataSources() throws Exception
    {
        return mitreService.getAllDataSources();
    }

    /**
     * 抓取FiGHT的name，只有一筆.
     * @return
     * @throws Exception
     */
    @GetMapping("/getName")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getName() throws Exception
    {
        final ObjectNode jsonNode = JsonNodeFactory.instance.objectNode();
        jsonNode.set("name", mitreService.getName());

        return jsonNode;
    }

//===================================Tactics=============================================================
    /**
     * 抓取全部tactics.
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllTactics")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllTactics() throws Exception
    {
        return mitreService.getAllTactics();
    }

    /**
     * 根據tacticId抓取tacticName.
     * @param tacticId
     * @return
     * @throws Exception
     */
    @GetMapping("/getTacticNameById")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getTacticNameById(@RequestParam final String tacticId) throws Exception
    {
        String tacticName = mitreService.getTacticNameById(tacticId);

        final ObjectNode jsonNode = JsonNodeFactory.instance.objectNode();
        if (tacticName == null || tacticName.isEmpty()){
            return jsonNode;
        }
        jsonNode.put("name", tacticName);

        return jsonNode;
    }

    /**
     * 根據tacticName抓取tacticId.
     * @param tacticName
     * @return
     * @throws Exception
     */
    @GetMapping("/getTacticIdByName")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getTacticIdByName(@RequestParam final String tacticName) throws Exception
    {
        String tacticId  = mitreService.getTacticIdByName(tacticName);

        final ObjectNode jsonNode = JsonNodeFactory.instance.objectNode();
        if (tacticId == null || tacticId.isEmpty()){
            return jsonNode;
        }
        jsonNode.put("id", tacticId);

        return jsonNode;
    }

    /**
     * 抓取全部tactics的name.
     * @return
     * @throws Exception
     */
    @GetMapping("/getTacticsByMatrix")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getTacticsByMatrix() throws Exception
    {
        return mitreService.getTacticsByMatrix();
    }

//===================================Techniques==========================================================
    /**
     * 抓取全部的techniques.
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllTechniques")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllTechniques() throws Exception
    {
        return mitreService.getAllTechniques();
    }

    /**
     * 抓取全部techniques內容，不包含subTechniques.
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllParentTechniques")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllParentTechniques() throws Exception
    {
        //沒有欄位叫"subTechnique-of"的話就是parent
        return mitreService.getAllParentTechniques();
    }

    /**
     * 抓取全部techniques內容，不包含subTechniques.，跟getAllParentTechniques一樣.
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllParentTechniquesOfAllSubTechniques")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllParentTechniquesOfAllSubTechniques() throws Exception
    {
        return mitreService.getAllParentTechniques();
    }

    /**
     * 根據全部mitigations抓取全部的techniques(不會出現空的mitigation).
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllTechniquesMitigatedByAllMitigations")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllTechniquesMitigatedByAllMitigations() throws Exception
    {
        return mitreService.getAllTechniquesMitigatedByAllMitigations();
    }

    /**
     * 根據tacticId抓取techniques.
     * @param tacticId
     * @return
     * @throws Exception
     */
    @GetMapping("/getTechniquesByTacticId")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getTechniquesByTacticId(@RequestParam final String tacticId) throws Exception
    {
        return mitreService.getTechniquesByTacticId(tacticId);
    }

    /**
     * 根據mitigationId抓取techniques內容.
     * @param mitigationId
     * @return
     * @throws Exception
     */
    @GetMapping("/getTechniqueMitigatedByMitigation")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getTechniqueMitigatedByMitigation(@RequestParam final String mitigationId) throws Exception
    {
        return mitreService.getTechniqueMitigatedByMitigation(mitigationId);
    }

    /**
     * 根據subTechniqueId抓取parentTechnique內容.
     * @param subTechniqueId
     * @return
     * @throws Exception
     */
    @GetMapping("/getParentTechniqueOfSubTechnique")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getParentTechniqueOfSubTechnique(@RequestParam final String subTechniqueId) throws Exception
    {
        return mitreService.getParentTechniqueOfSubTechnique(subTechniqueId);
    }

//===================================SubTechniques=======================================================
    /**
     * 抓取全部的subTechniques.
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllSubTechniques")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllSubTechniques() throws Exception
    {
        return mitreService.getAllSubTechniques();
    }

    /**
     * 抓全部的subTechniques上層有屬於techniques的內容，跟getAllSubTechniques一樣.
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllSubTechniquesOfAllTechniques")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllSubTechniquesOfAllTechniques() throws Exception
    {
        return mitreService.getAllSubTechniques();
    }

    /**
     * 根據techniqueId抓取subTechniques.
     * @param techniqueId
     * @return
     * @throws Exception
     */
    @GetMapping("/getSubTechniquesOfTechnique")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getSubTechniquesOfTechnique(@RequestParam String techniqueId) throws Exception
    {
        return mitreService.getSubTechniquesOfTechnique(techniqueId);
    }

//===================================Mitigations=========================================================
    /**
     * 抓取全部的mitigations.
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllMitigations")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllMitigations() throws Exception
    {
        return mitreService.getAllMitigations();
    }

    /**
     * 抓全部mitigations可以實際緩解techniques的內容.
     * @return
     * @throws Exception
     */
    @GetMapping("/getAllMitigationsMitigatingAllTechniques")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getAllMitigationsMitigatingAllTechniques() throws Exception
    {
        return mitreService.getAllMitigationsMitigatingAllTechniques();
    }

    /**
     * 抓mitigation可以實際緩解technique並且符合使用者輸入的techniqueId.
     * @param techniqueId
     * @return
     * @throws Exception
     */
    @GetMapping("/getMitigationsMitigatingTechnique")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getMitigationsMitigatingTechnique(@RequestParam String techniqueId) throws Exception
    {
        return mitreService.getMitigationsMitigatingTechnique(techniqueId);
    }

    /**
     * 根據techniqueId抓取mitigation內容.
     * @param techniqueId
     * @return
     * @throws Exception
     */
    @GetMapping("/getMitigationsByTechniqueId")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getMitigationsByTechniqueId(@RequestParam String techniqueId) throws Exception
    {
        return mitreService.getMitigationsByTechniqueId(techniqueId);
    }
//===================================Objects=============================================================
    /**
     * 根據id抓取全部內容，(id = FGT1048 , TA0043 , FGM1499 , DS0008...)
     * @param stixId
     * @return
     * @throws Exception
     */
    @GetMapping("/getObjectByStixId")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getObjectByStixId(@RequestParam String stixId) throws Exception
    {
        return mitreService.getObjectByStixId(stixId);
    }

    /**
     * 根據抓typecode 是否存在 抓取全部內容，(typecode = techniques,subTechniques)
     * @param attackId
     * @return
     * @throws Exception
     */
    @GetMapping("/getObjectByAttackId")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getObjectByAttackId(@RequestParam String attackId) throws Exception
    {
        return mitreService.getObjectByAttackId(attackId);
    }

    /**
     * 根據name抓全部符合的內容(name = Reconnaissance , Rootkit ....)
     * @param name
     * @return
     * @throws Exception
     */
    @GetMapping("/getObjectByName")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getObjectByName(@RequestParam String name) throws Exception
    {
        return mitreService.getObjectByName(name);
    }

    /**
     * 根據type抓全部符合的內容.(type: technique,tactic,mitigation,data source)
     * @param type
     * @return
     * @throws Exception
     */
    @GetMapping("/getObjectByType")
    @ResponseStatus(HttpStatus.OK)
    public JsonNode getObjectByType(@RequestParam String type) throws Exception
    {
        return mitreService.getObjectByType(type);
    }
}
