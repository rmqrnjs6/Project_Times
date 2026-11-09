package dev.projecttimes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class WorkbenchService {
    private final Database database;
    private final AnalysisService analysis;
    private final ObjectMapper json;
    private final ExternalMaterialPolicy materials;
    public WorkbenchService(Database database, AnalysisService analysis, ObjectMapper json, ExternalMaterialPolicy materials) {
        this.database=database; this.analysis=analysis; this.json=json; this.materials=materials;
    }
    public void ready() throws Exception { database.ready(); analysis.ready(); }
    public Object checks(String owner) throws Exception { return database.checks(owner); }
    public Object saveCheck(String owner, String question) throws Exception {
        if (question == null || question.isBlank() || question.length()>InputLimits.QUESTION_CHARS) throw new RequestFailure(400,"invalid_question");
        analysis.ready();
        return Map.of("id",database.saveCheck(owner,question.strip()).toString(),"kind","connectivity_only","analysis","ok");
    }
    public JsonNode evaluate(String owner, JsonNode input) throws Exception {
        if (!input.isObject()) throw new RequestFailure(400,"invalid_evaluation");
        var response=analysis.call("/evaluate",json.writeValueAsString(input));
        if (response.statusCode()==422) throw new RequestFailure(422,"invalid_evaluation");
        if (response.statusCode()!=200) throw new RequestFailure(503,"dependency_unavailable");
        var output=json.readTree(response.body());
        if (!output.isObject() || !output.hasNonNull("policy_version")) throw new RequestFailure(503,"invalid_analysis_response");
        var id=database.saveCalculation(owner,output.get("policy_version").asText(),json.writeValueAsString(input),response.body());
        ((com.fasterxml.jackson.databind.node.ObjectNode)output).put("run_id",id.toString());
        return output;
    }
    public Object material(String owner, String source, String content, String mediaType) throws Exception {
        var accepted=materials.inspect(source,content,mediaType);
        var id=database.saveMaterial(owner,accepted.source(),content,accepted.sha256());
        return Map.of("id",id.toString(),"sha256",accepted.sha256(),"status","quarantined","trusted",false,"fetched",false);
    }
}
