package dev.playsense.dashboard;

import dev.playsense.player.PlayerService;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboard;
    private final PlayerService players;

    public DashboardController(DashboardService dashboard, PlayerService players) {
        this.dashboard = dashboard;
        this.players = players;
    }

    @GetMapping("/api/dashboard")
    public DashboardView dashboard(@RequestAttribute("steamid") long steamid) {
        return dashboard.load(steamid, false);
    }

    @PostMapping("/api/refresh")
    public DashboardView refresh(@RequestAttribute("steamid") long steamid) {
        return dashboard.load(steamid, true);
    }

    @GetMapping("/api/me")
    public Me me(@RequestAttribute("steamid") long steamid) {
        var player = players.find(steamid).orElseThrow();
        return new Me(player.steamid(), player.personaname(), player.avatarUrl(), player.libraryPrivate(),
                player.trackedSince());
    }

    public record Me(long steamid, String name, String avatar, boolean libraryPrivate, Instant trackedSince) {}
}
