package dev.projecttimes;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class TaskService {
    private final TaskStore store;
    public TaskService(TaskStore store) { this.store=store; }
    public UUID create(String owner,String question,String requirement) throws Exception {
        if(question==null || question.isBlank() || question.length()>InputLimits.QUESTION_CHARS) throw new RequestFailure(400,"invalid_question");
        if(requirement==null || !Set.of("PRIMARY_REQUIRED","CROSS_CHECK_REQUIRED").contains(requirement))
            throw new RequestFailure(400,"invalid_evidence_requirement");
        return store.create(owner,question.strip(),requirement);
    }
    public UUID note(String owner,String task,String source,String original,String interpretation,String stance) throws Exception {
        UUID taskId=id(task);
        if(original==null || original.isBlank() || original.length()>InputLimits.NOTE_CHARS || interpretation==null || interpretation.isBlank() || interpretation.length()>InputLimits.NOTE_CHARS)
            throw new RequestFailure(400,"invalid_note_text");
        if(stance==null || !Set.of("SUPPORTS","CONTRADICTS","UNVERIFIED").contains(stance)) throw new RequestFailure(400,"invalid_note_stance");
        if(source==null || source.length()>InputLimits.SOURCE_URL_CHARS) throw new RequestFailure(400,"invalid_source_url");
        try {
            var uri=new java.net.URI(source);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getRawQuery()!=null || uri.getFragment()!=null || (uri.getPort()!=-1 && uri.getPort()!=443)) throw new IllegalArgumentException();
        } catch(Exception e) { throw new RequestFailure(400,"invalid_source_url"); }
        // Reference only: no download, parser, DNS lookup, AI processing, or trust certification.
        return store.addNote(owner,taskId,source,original,interpretation,stance);
    }
    private UUID id(String value) {
        try { var id=UUID.fromString(value); if(!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); return id; }
        catch(IllegalArgumentException e) { throw new RequestFailure(400,"invalid_task_id"); }
    }
    public Object list(String owner) throws Exception { store.recoverExpired(owner); return store.list(owner); }
    public Object detail(String owner,String value) throws Exception { UUID id=id(value); store.recoverExpired(owner); return store.detail(owner,id); }
    public void cancel(String owner,String value,Long version) throws Exception {
        UUID id=id(value);
        if(version==null || version<0) throw new RequestFailure(400,"invalid_task_version");
        store.recoverExpired(owner); store.cancel(owner,id,version);
    }
}
