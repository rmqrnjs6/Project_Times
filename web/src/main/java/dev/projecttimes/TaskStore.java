package dev.projecttimes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Each mutation and its event share one JDBC transaction. No remote calls under row locks. */
@Repository
public class TaskStore {
    private final DataSource source;
    private final ObjectMapper json;
    public TaskStore(DataSource source, ObjectMapper json) { this.source=source; this.json=json; }
    @FunctionalInterface private interface Work<T> { T run(Connection c) throws Exception; }
    private <T> T transaction(Work<T> work) throws Exception {
        try (var c=source.getConnection()) {
            c.setAutoCommit(false);
            try { T value=work.run(c); c.commit(); return value; }
            catch (Exception e) { c.rollback(); throw e; }
        }
    }
    public UUID create(String owner,String question,String requirement) throws Exception {
        return transaction(c -> {
            var id=UUID.randomUUID();
            try (var s=c.prepareStatement("INSERT INTO verification_tasks(id,owner_key,question,evidence_requirement) VALUES (?,?,?,?)")) {
                s.setObject(1,id); s.setString(2,owner); s.setString(3,question); s.setString(4,requirement); s.executeUpdate();
            }
            event(c,id,0,null,"QUEUED",owner,"CREATED");
            return id;
        });
    }
    private void event(Connection c,UUID id,long version,String from,String to,String actor,String reason) throws SQLException {
        try (var s=c.prepareStatement("INSERT INTO verification_task_events(task_id,version,from_status,to_status,actor,reason) VALUES (?,?,?,?,?,?)")) {
            s.setObject(1,id); s.setLong(2,version); s.setString(3,from); s.setString(4,to); s.setString(5,actor); s.setString(6,reason); s.executeUpdate();
        }
    }
    private Map<String,Object> row(ResultSet r) throws Exception {
        var out=new LinkedHashMap<String,Object>();
        for (String name:List.of("id","question","evidence_requirement","status","created_at","updated_at","failure_code"))
            out.put(name,r.getString(name));
        out.put("version",r.getLong("version"));
        String result=r.getString("result"); out.put("result",result==null?null:json.readTree(result));
        return out;
    }
    public List<Map<String,Object>> list(String owner) throws Exception {
        var rows=new ArrayList<Map<String,Object>>();
        try(var c=source.getConnection();var s=c.prepareStatement("SELECT * FROM verification_tasks WHERE owner_key=? ORDER BY created_at DESC,id LIMIT 50")) {
            s.setString(1,owner); try(var r=s.executeQuery()) { while(r.next()) rows.add(row(r)); }
        }
        return rows;
    }
    public Map<String,Object> detail(String owner,UUID id) throws Exception {
        return transaction(c -> {
            // Hold a shared lock so task and event snapshots cannot straddle a concurrent transition.
            Map<String,Object> task;
            try(var s=c.prepareStatement("SELECT * FROM verification_tasks WHERE id=? AND owner_key=? FOR SHARE")) {
                s.setObject(1,id); s.setString(2,owner);
                try(var r=s.executeQuery()) { if(!r.next()) throw new RequestFailure(404,"task_not_found"); task=row(r); }
            }
            var events=new ArrayList<Map<String,Object>>();
            try(var s=c.prepareStatement("SELECT * FROM verification_task_events WHERE task_id=? ORDER BY version")) {
                s.setObject(1,id);
                try(var r=s.executeQuery()) { while(r.next()) {
                    var e=new LinkedHashMap<String,Object>();
                    e.put("version",r.getLong("version"));
                    for(String name:List.of("from_status","to_status","actor","reason","created_at")) e.put(name,r.getString(name));
                    events.add(e);
                }}
            }
            task.put("events",events); return task;
        });
    }
    public void cancel(String owner,UUID id,long expectedVersion) throws Exception {
        transaction(c -> {
            String from; long version;
            try(var s=c.prepareStatement("SELECT status,version FROM verification_tasks WHERE id=? AND owner_key=? FOR UPDATE")) {
                s.setObject(1,id); s.setString(2,owner);
                try(var r=s.executeQuery()) {
                    if(!r.next()) throw new RequestFailure(404,"task_not_found");
                    from=r.getString(1); version=r.getLong(2);
                }
            }
            if(version!=expectedVersion) throw new RequestFailure(409,"task_version_conflict");
            if(!Set.of("QUEUED","RUNNING").contains(from)) throw new RequestFailure(409,"task_terminal");
            try(var s=c.prepareStatement("UPDATE verification_tasks SET status='CANCELLED',version=version+1,lease_token=NULL,lease_until=NULL,updated_at=clock_timestamp() WHERE id=?")) {
                s.setObject(1,id); s.executeUpdate();
            }
            event(c,id,version+1,from,"CANCELLED",owner,"USER_CANCELLED"); return null;
        });
    }
    /** Internal worker boundary. Never exposed through HTTP. Token fences late worker responses. */
    public UUID claim(UUID id) throws Exception {
        return transaction(c -> {
            UUID token=UUID.randomUUID(); long version;
            try(var s=c.prepareStatement("UPDATE verification_tasks SET status='RUNNING',version=version+1,lease_token=?,lease_until=clock_timestamp()+interval '120 seconds',updated_at=clock_timestamp() WHERE id=? AND status='QUEUED' RETURNING version")) {
                s.setObject(1,token); s.setObject(2,id);
                try(var r=s.executeQuery()) { if(!r.next()) return null; version=r.getLong(1); }
            }
            event(c,id,version,"QUEUED","RUNNING","system:worker","CLAIMED"); return token;
        });
    }
    public boolean finish(UUID id,UUID token,JsonNode result,String failureCode) throws Exception {
        boolean success=result!=null && !result.isNull();
        if(success == (failureCode!=null) || (success && !result.isObject()) ||
           (!success && !Set.of("EXECUTION_FAILED","DEPENDENCY_UNAVAILABLE").contains(failureCode==null?"":failureCode)))
            throw new IllegalArgumentException("invalid task outcome");
        return transaction(c -> {
            long version; String target=success?"SUCCEEDED":"FAILED";
            try(var s=c.prepareStatement("UPDATE verification_tasks SET status=?,result=?::jsonb,failure_code=?,version=version+1,lease_token=NULL,lease_until=NULL,updated_at=clock_timestamp() WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>clock_timestamp() RETURNING version")) {
                s.setString(1,target); s.setString(2,success?json.writeValueAsString(result):null); s.setString(3,failureCode);
                s.setObject(4,id); s.setObject(5,token);
                try(var r=s.executeQuery()) { if(!r.next()) return false; version=r.getLong(1); }
            }
            event(c,id,version,"RUNNING",target,"system:worker",success?"COMPLETED":failureCode); return true;
        });
    }
    /** Lazy lease recovery: only expired running jobs for this owner; queued jobs stay queued. */
    public void recoverExpired(String owner) throws Exception {
        transaction(c -> {
            var changed=new ArrayList<Map.Entry<UUID,Long>>();
            try(var s=c.prepareStatement("UPDATE verification_tasks SET status='FAILED',failure_code='EXECUTION_INTERRUPTED',version=version+1,lease_token=NULL,lease_until=NULL,updated_at=clock_timestamp() WHERE owner_key=? AND status='RUNNING' AND lease_until<=clock_timestamp() RETURNING id,version")) {
                s.setString(1,owner);
                try(var r=s.executeQuery()) { while(r.next()) changed.add(Map.entry(r.getObject(1,UUID.class),r.getLong(2))); }
            }
            for(var entry:changed) event(c,entry.getKey(),entry.getValue(),"RUNNING","FAILED","system:recovery","EXECUTION_INTERRUPTED");
            return null;
        });
    }
}
