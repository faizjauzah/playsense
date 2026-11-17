package dev.playsense.auth;

import dev.playsense.player.PlayerService;
import dev.playsense.steam.SteamClient;
import dev.playsense.steam.SteamClient.PlayerSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identity by assertion: a vanity name or SteamID64, resolved through Steam. No credentials, ever.
 * The trade is that anyone who knows an ID can open that dashboard — see docs/adr/0004.
 */
@RestController
@RequestMapping("/api/session")
public class AuthController {

    private static final Pattern STEAM_ID = Pattern.compile("^7656119\\d{10}$");
    private static final Pattern VANITY = Pattern.compile("^[A-Za-z0-9_.-]{2,32}$");
    private static final int LOOKUPS_PER_MINUTE = 6;

    private final SteamClient steam;
    private final PlayerService players;
    private final SessionService sessions;
    private final boolean secureCookies;
    /** This endpoint is public and each call spends Steam API quota, so it gets a floor. */
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public AuthController(SteamClient steam,
                          PlayerService players,
                          SessionService sessions,
                          @Value("${playsense.cookie-secure}") boolean secureCookies) {
        this.steam = steam;
        this.players = players;
        this.sessions = sessions;
        this.secureCookies = secureCookies;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SessionRequest request,
                                    HttpServletRequest http,
                                    HttpServletResponse response) {
        if (!allowed(http.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many lookups. Wait a minute and try again."));
        }

        String input = request.steamIdOrVanity() == null ? "" : request.steamIdOrVanity().trim();
        long steamid;
        if (STEAM_ID.matcher(input).matches()) {
            steamid = Long.parseLong(input);
        } else if (VANITY.matcher(input).matches()) {
            steamid = steam.resolveVanity(input).orElse(0L);
            if (steamid == 0L) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No Steam account has that name."));
            }
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Enter a Steam vanity name or a 17-digit SteamID64."));
        }

        var summary = steam.summary(steamid);
        if (summary.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Steam has no profile with that ID."));
        }

        PlayerSummary player = summary.get();
        players.upsert(player);

        String session = sessions.create(steamid);
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(SessionService.COOKIE, session, Duration.ofDays(30)).toString());

        return ResponseEntity.ok(Map.of("steamid", steamid, "name", player.personaname()));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@CookieValue(name = SessionService.COOKIE, required = false) String session,
                                       HttpServletResponse response) {
        sessions.delete(session);
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(SessionService.COOKIE, "", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    /** True when this address has made fewer than LOOKUPS_PER_MINUTE calls in the last minute. */
    private boolean allowed(String address) {
        long now = System.currentTimeMillis();
        Deque<Long> recent = attempts.computeIfAbsent(address == null ? "unknown" : address,
                key -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && now - recent.peekFirst() > 60_000) {
                recent.pollFirst();
            }
            if (recent.size() >= LOOKUPS_PER_MINUTE) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }

    private ResponseCookie cookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public record SessionRequest(String steamIdOrVanity) {}
}
