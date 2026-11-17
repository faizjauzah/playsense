package dev.playsense.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Sessions live in Postgres: the free Render instance restarts often and an in-memory session would log everyone out. */
@Service
public class SessionService {

    public static final String COOKIE = "ps_session";

    private final JdbcClient db;

    public SessionService(JdbcClient db) {
        this.db = db;
    }

    public String create(long steamid) {
        String id = UUID.randomUUID().toString();
        db.sql("insert into login_session (id, steamid) values (?, ?)")
                .params(id, steamid)
                .update();
        return id;
    }

    /** Sliding expiry in one statement: reading a session also renews it. */
    public Optional<Long> resolve(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return db.sql("""
                        update login_session set last_used_at = now()
                        where id = ? and last_used_at > now() - interval '30 days'
                        returning steamid
                        """)
                .param(id)
                .query(Long.class)
                .optional();
    }

    public void delete(String id) {
        if (id != null && !id.isBlank()) {
            db.sql("delete from login_session where id = ?").param(id).update();
        }
    }
}
