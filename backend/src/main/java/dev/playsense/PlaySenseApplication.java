package dev.playsense;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PlaySenseApplication {

    /**
     * Render and Neon both hand out postgres://user:pass@host/db URLs, which the JDBC driver cannot read.
     * Split it into a JDBC URL plus credentials before Spring starts.
     */
    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.startsWith("jdbc:")) {
            URI uri = URI.create(databaseUrl);
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath()
                    + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
            System.setProperty("spring.datasource.url", jdbc);

            String userInfo = uri.getUserInfo();
            if (userInfo != null && userInfo.contains(":")) {
                String[] credentials = userInfo.split(":", 2);
                System.setProperty("spring.datasource.username",
                        URLDecoder.decode(credentials[0], StandardCharsets.UTF_8));
                System.setProperty("spring.datasource.password",
                        URLDecoder.decode(credentials[1], StandardCharsets.UTF_8));
            }
        }
        SpringApplication.run(PlaySenseApplication.class, args);
    }
}
