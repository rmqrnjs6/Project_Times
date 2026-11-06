package dev.projecttimes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL tests. CI supplies an isolated database; local runs skip without TASK_TEST_DB_URL. */
class TaskStoreTest {
    static DriverManagerDataSource source;
    static TaskStore store;
    static ObjectMapper json=new ObjectMapper();
    UUID task;
    @BeforeAll static void database() {
        String url=System.getenv("TASK_TEST_DB_URL");
        Assumptions.assumeTrue(url!=null,"TASK_TEST_DB_URL required for PostgreSQL tests");
        source=new DriverManagerDataSource(url,System.getenv("TASK_TEST_DB_USER"),System.getenv("TASK_TEST_DB_PASSWORD"));
        Flyway.configure().dataSource(source).load().migrate();
        store=new TaskStore(source,json);
    }
    @BeforeEach void owner() throws Exception {
        try(var c=source.getConnection();var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO security_users(username,password) VALUES ('task-alice','unused-test-hash'),('task-bob','unused-test-hash') ON CONFLICT DO NOTHING");
        }
        task=store.create("task-alice","synthetic question","PRIMARY_REQUIRED");
    }
    @Test void originalInputAndCreationEventPersistTogether() throws Exception {
        var row=store.detail("task-alice",task);
        assertEquals("QUEUED",row.get("status")); assertEquals("synthetic question",row.get("question"));
        assertEquals(1,((List<?>)row.get("events")).size());
        assertTrue(store.list("task-alice").stream().anyMatch(r -> task.toString().equals(r.get("id"))));
    }
    @Test void otherOwnerGetsSameNotFoundAsUnknownTask() throws Exception {
        assertEquals(404,assertThrows(RequestFailure.class,()->store.detail("task-bob",task)).status);
        assertEquals(404,assertThrows(RequestFailure.class,()->store.cancel("task-bob",task,0)).status);
        assertEquals(404,assertThrows(RequestFailure.class,()->store.detail("task-bob",UUID.randomUUID())).status);
        assertTrue(store.list("task-bob").stream().noneMatch(r -> task.toString().equals(r.get("id"))));
    }
    @Test void staleCancelAndTerminalChangesRejected() throws Exception {
        assertEquals(409,assertThrows(RequestFailure.class,()->store.cancel("task-alice",task,1)).status);
        store.cancel("task-alice",task,0);
        assertEquals(409,assertThrows(RequestFailure.class,()->store.cancel("task-alice",task,1)).status);
        assertNull(store.claim(task));
        assertEquals(2,((List<?>)store.detail("task-alice",task).get("events")).size());
    }
    @Test void onlyMatchingLeaseCanCompleteAndResultPersists() throws Exception {
        UUID token=store.claim(task);
        assertNotNull(token); assertNull(store.claim(task));
        assertFalse(store.finish(task,UUID.randomUUID(),json.readTree("{\"synthetic\":true}"),null));
        assertTrue(store.finish(task,token,json.readTree("{\"synthetic\":true}"),null));
        var row=store.detail("task-alice",task);
        assertEquals("SUCCEEDED",row.get("status")); assertEquals(json.readTree("{\"synthetic\":true}"),row.get("result"));
        assertFalse(store.finish(task,token,null,"EXECUTION_FAILED"));
        assertEquals(3,((List<?>)row.get("events")).size());
    }
    @Test void failureUsesSafeCodeAndNoResult() throws Exception {
        UUID token=store.claim(task);
        assertTrue(store.finish(task,token,null,"DEPENDENCY_UNAVAILABLE"));
        var row=store.detail("task-alice",task);
        assertEquals("FAILED",row.get("status")); assertEquals("DEPENDENCY_UNAVAILABLE",row.get("failure_code")); assertNull(row.get("result"));
    }
    @Test void cancellationFencesLateWorker() throws Exception {
        UUID token=store.claim(task); store.cancel("task-alice",task,1);
        assertFalse(store.finish(task,token,json.createObjectNode(),null));
        assertEquals("CANCELLED",store.detail("task-alice",task).get("status"));
    }
    @Test void expiredLeaseRecoveredOnceAndQueuedPreserved() throws Exception {
        UUID queued=store.create("task-alice","queued","CROSS_CHECK_REQUIRED");
        UUID token=store.claim(task);
        try(var c=source.getConnection();var s=c.prepareStatement("UPDATE verification_tasks SET lease_until=clock_timestamp()-interval '1 second' WHERE id=?")) {
            s.setObject(1,task); s.executeUpdate();
        }
        assertFalse(store.finish(task,token,json.createObjectNode(),null));
        store.recoverExpired("task-bob"); assertEquals("RUNNING",store.detail("task-alice",task).get("status"));
        store.recoverExpired("task-alice"); store.recoverExpired("task-alice");
        var row=store.detail("task-alice",task);
        assertEquals("FAILED",row.get("status")); assertEquals("EXECUTION_INTERRUPTED",row.get("failure_code"));
        assertEquals(3,((List<?>)row.get("events")).size());
        assertEquals("QUEUED",store.detail("task-alice",queued).get("status"));
    }
    @Test void failedEventInsertRollsBackStatusUpdate() throws Exception {
        // Preoccupy the next event version: the real insert must fail after the UPDATE.
        try(var c=source.getConnection();var s=c.prepareStatement("INSERT INTO verification_task_events(task_id,version,to_status,actor,reason) VALUES (?,1,'CANCELLED','test','FAULT_INJECTION')")) {
            s.setObject(1,task); s.executeUpdate();
        }
        assertThrows(SQLException.class,()->store.cancel("task-alice",task,0));
        var row=store.detail("task-alice",task);
        assertEquals("QUEUED",row.get("status")); assertEquals(0L,row.get("version"));
    }
    @Test void concurrentClaimHasExactlyOneWinner() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<UUID> claim=()->{start.await();return store.claim(task);};
            var a=pool.submit(claim);var b=pool.submit(claim); start.countDown();
            var first=a.get(10,TimeUnit.SECONDS);var second=b.get(10,TimeUnit.SECONDS);
            assertTrue((first==null)!=(second==null));
            assertEquals(2,((List<?>)store.detail("task-alice",task).get("events")).size());
        }
    }
    @Test void concurrentCancelAndCompleteLeaveOneTerminalEvent() throws Exception {
        UUID token=store.claim(task);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var cancel=pool.submit(()->{ start.await(); try {store.cancel("task-alice",task,1);return true;}
                catch(RequestFailure e) {assertEquals(409,e.status);return false;} });
            var finish=pool.submit(()->{start.await();return store.finish(task,token,json.createObjectNode(),null);});
            start.countDown();
            assertNotEquals(cancel.get(10,TimeUnit.SECONDS),finish.get(10,TimeUnit.SECONDS));
            var row=store.detail("task-alice",task);
            assertTrue(Set.of("CANCELLED","SUCCEEDED").contains(row.get("status")));
            assertEquals(2L,row.get("version")); assertEquals(3,((List<?>)row.get("events")).size());
        }
    }

    @Test void manualNotesPreserveOriginalAndCorrectionsWithoutChangingExecutionStatus() throws Exception {
        store.addNote("task-alice",task,"https://example.org/source"," original <script> ","first interpretation","SUPPORTS");
        store.addNote("task-alice",task,"https://example.org/source","new excerpt","correction of first interpretation","UNVERIFIED");
        var row=store.detail("task-alice",task);
        var notes=(List<?>)row.get("notes");
        assertEquals(2,notes.size());
        assertEquals(" original <script> ",((Map<?,?>)notes.get(0)).get("original_text"));
        assertEquals("SUPPORTS",((Map<?,?>)notes.get(0)).get("stance"));
        assertEquals("UNVERIFIED",((Map<?,?>)notes.get(1)).get("stance"));
        assertEquals("QUEUED",row.get("status")); assertEquals(0L,row.get("version"));
    }
    @Test void otherOwnerCannotAppendNotesAndFailedInsertLeavesNoNote() throws Exception {
        assertEquals(404,assertThrows(RequestFailure.class,()->store.addNote("task-bob",task,"https://example.org","text","reason","SUPPORTS")).status);
        assertThrows(SQLException.class,()->store.addNote("task-alice",task,"https://example.org","text","reason","INVALID"));
        assertEquals(0,((List<?>)store.detail("task-alice",task).get("notes")).size());
    }
}
