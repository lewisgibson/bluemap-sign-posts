package com.lewisgibson.bluemapsignposts.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The plugin on a real Paper 26.3 server with a real BlueMap, once per storage backend: it enables,
 * opens its store, BlueMap comes up and the markers appear in BlueMap's own marker set, the limits
 * and the admin commands do what the unit tests say they do, and a {@code /bluemap reload} does not
 * lose a marker - all observed through the server's own event pipeline and commands over RCON, with
 * nothing of the plugin's internals reached into.
 */
@DisplayName("BlueMapSignPosts on a real Paper server")
class PaperServerTest {

    private static final Path PLUGIN_JAR = Path.of(System.getProperty("bmsp.pluginJar"));
    private static final Path FIXTURE_JAR = Path.of(System.getProperty("bmsp.fixtureJar"));
    private static final Path WORK = Path.of(System.getProperty("bmsp.workDir"));
    private static final long ONE_HOUR = 72_000L;

    /** A previous run's world and store must not leak into this one. */
    static Path freshDirectory(Path directory) throws java.io.IOException {
        if (Files.exists(directory)) {
            try (var walk = Files.walk(directory)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(
                                path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (java.io.IOException ex) {
                                        throw new java.io.UncheckedIOException(ex);
                                    }
                                });
            }
        }
        return Files.createDirectories(directory);
    }

    @BeforeAll
    static void requireDocker() {
        Docker.requireDocker();
        assertThat(PLUGIN_JAR).exists();
        assertThat(FIXTURE_JAR).exists();
    }

    @ParameterizedTest(name = "storage: {0}")
    @ValueSource(strings = {"sqlite", "postgres", "postgres-tls", "mariadb", "mysql"})
    void markersOnRealServer(String backend) throws Exception {
        Path data = freshDirectory(WORK.resolve(backend));
        MySqlServer.Flavour flavour =
                backend.equals("mysql") ? MySqlServer.Flavour.MYSQL : MySqlServer.Flavour.MARIADB;
        try (PostgresServer postgres = new PostgresServer();
                MySqlServer mysql = new MySqlServer(flavour);
                PaperServer paper = new PaperServer(data)) {
            String storage;
            if (backend.equals("mariadb") || backend.equals("mysql")) {
                mysql.start();
                storage =
                        """
                        storage:
                            type: %s
                            mysql:
                                host: %s
                                port: 3306
                                database: %s
                                user: %s
                                password: %s
                        """
                                .formatted(
                                        backend,
                                        mysql.bridgeAddress(),
                                        MySqlServer.DATABASE,
                                        MySqlServer.USER,
                                        MySqlServer.PASSWORD);
            } else if (backend.startsWith("postgres")) {
                postgres.start();
                boolean tls = backend.equals("postgres-tls");
                if (tls) {
                    postgres.requireTls(WORK.resolve(backend + "-certificates"));
                }
                // With TLS, pg_hba refuses every connection that is not TLS, and verify-full
                // checks the certificate against the test CA and the host against its address.
                storage =
                        """
                        storage:
                            type: postgres
                            postgres:
                                host: %s
                                port: 5432
                                database: %s
                                user: %s
                                password: %s
                                ssl-mode: %s
                                ssl-root-cert: '%s'
                        """
                                .formatted(
                                        postgres.bridgeAddress(),
                                        PostgresServer.DATABASE,
                                        PostgresServer.USER,
                                        PostgresServer.PASSWORD,
                                        tls ? "verify-full" : "disable",
                                        tls ? "/data/plugins/BlueMapSignPosts/ca.crt" : "");
            } else {
                storage = "storage:\n    type: sqlite\n";
            }
            paper.install(PLUGIN_JAR, FIXTURE_JAR, "limits:\n    default: 2\n" + storage);
            if (backend.equals("postgres-tls")) {
                Files.copy(
                        postgres.caCertificate(), data.resolve("plugins/BlueMapSignPosts/ca.crt"));
            }

            String boot = paper.start();
            assertThat(boot).contains("Enabling BlueMapSignPosts v");
            assertThat(boot)
                    .contains(
                            "[BlueMapSignPosts] Store: "
                                    + switch (backend) {
                                        case "postgres", "postgres-tls" -> "postgres";
                                        case "mariadb", "mysql" -> "mysql";
                                        default -> backend;
                                    });
            if (backend.equals("postgres-tls")) {
                assertThat(boot).contains("sslmode verify-full");
            }
            assertThat(boot).doesNotContain("Error occurred while enabling BlueMapSignPosts");
            assertThat(boot).contains("Enabling BlueMapSignPostsTestFixture");
            paper.awaitPublished(1);

            // A [map] sign becomes a marker in BlueMap's own marker set, and the player is told.
            String created = paper.rcon("bmsptest sign 0 64 0 0 - [map]|Iron farm||");
            assertThat(created)
                    .contains("Added to the map: Iron farm")
                    .contains("marker=Iron farm");
            assertThat(paper.rcon("bmsptest marker 0 64 0")).isEqualTo("Iron farm");
            assertThat(paper.rcon("signposts list TestPlayer"))
                    .contains("1 of 2")
                    .contains("Iron farm");

            // An admin override through the real command and the real store beats the default.
            assertThat(paper.rcon("signposts limit set TestPlayer 1")).contains("limit is now 1");
            assertThat(paper.rcon("bmsptest read 0 64 0")).startsWith("sign [map]|Iron farm||");
            String refused = paper.rcon("bmsptest sign 1 64 1 0 - [map]|Two||");
            assertThat(refused).contains("limit is 1").contains("marker=-");
            assertThat(paper.rcon("signposts limit TestPlayer"))
                    .contains("1 (1 used)")
                    .contains("admin override set by console");
            assertThat(paper.rcon("signposts limit clear TestPlayer")).contains("cleared");

            // A renamed banner becomes a marker too.
            String banner = paper.rcon("bmsptest banner 2 64 2 0 - Bob's base");
            assertThat(banner)
                    .contains("Added to the map: Bob's base")
                    .contains("marker=Bob's base");

            // Permission and playtime gates, through the server's own Permissible calls.
            assertThat(paper.rcon("bmsptest sign 3 64 3 0 noperm [map]|Nope||"))
                    .contains("can't add markers")
                    .contains("marker=-");
            paper.writeConfig("limits:\n    default: 2\nrequired-playtime: 1h\n" + storage);
            assertThat(paper.rcon("signposts reload")).contains("reloaded");
            assertThat(paper.rcon("bmsptest sign 3 64 3 0 - [map]|Early||"))
                    .contains("1h")
                    .contains("marker=-");
            assertThat(paper.rcon("bmsptest sign 3 64 3 " + ONE_HOUR + " bypass [map]|Staff||"))
                    .contains("Added to the map: Staff");

            // Breaking the block removes the marker; the admin command removes one by coordinates.
            assertThat(paper.rcon("bmsptest break 0 64 0"))
                    .contains("Removed from the map: Iron farm");
            assertThat(paper.rcon("bmsptest marker 0 64 0")).isEqualTo("-");
            assertThat(paper.rcon("signposts remove world 2 64 2"))
                    .contains("Removed \"Bob's base\"");
            assertThat(paper.rcon("bmsptest marker 2 64 2")).isEqualTo("-");
            assertThat(paper.rcon("signposts list all"))
                    .contains("Signposts on the map")
                    .contains("Staff")
                    .doesNotContain("Iron farm")
                    .doesNotContain("Bob's base");

            // A real explosion takes a sign and its marker without any break event.
            assertThat(paper.rcon("bmsptest sign 12 64 12 " + ONE_HOUR + " bypass [map]|Mine||"))
                    .contains("marker=Mine");
            assertThat(paper.rcon("bmsptest explode 12 64 12")).contains("now=AIR");
            assertThat(paper.rcon("bmsptest marker 12 64 12")).isEqualTo("-");

            // BlueMap forgets API markers on reload; the plugin re-publishes from its store.
            assertThat(paper.rcon("bluemap reload")).isNotNull();
            paper.awaitPublished(2);
            assertThat(paper.rcon("bmsptest marker 3 64 3")).isEqualTo("Staff");

            if (backend.equals("sqlite")) {
                everyWorld(paper);
            }

            assertThat(paper.logs()).doesNotContain("A store write failed");
            if (backend.equals("sqlite")) {
                assertThat(data.resolve("plugins/BlueMapSignPosts/data.db")).exists();
            }
        }
    }

    /**
     * The rest of the compatibility list, once: the Nether and the End have their own maps, and a
     * world that loads after the server started, unloads and loads again keeps its signposts.
     */
    private static void everyWorld(PaperServer paper) throws Exception {
        assertThat(paper.rcon("bmsptest @nether sign 0 64 0 " + ONE_HOUR + " bypass [map]|Hub||"))
                .contains("marker=Hub");
        assertThat(paper.rcon("bmsptest @end sign 0 64 0 " + ONE_HOUR + " bypass [map]|Island||"))
                .contains("marker=Island");

        assertThat(paper.rcon("bmsptest worldcreate extra")).contains("created=true");
        assertThat(paper.rcon("bmsptest @extra sign 0 64 0 " + ONE_HOUR + " bypass [map]|Far||"))
                .contains("Added to the map: Far");
        assertThat(paper.rcon("bmsptest worldunload extra")).contains("unloaded=true");
        assertThat(paper.rcon("signposts list world extra")).contains("Far");
        assertThat(paper.rcon("bmsptest worldcreate extra")).contains("created=true");
        assertThat(paper.rcon("signposts list world extra")).contains("Far");
        assertThat(paper.logs()).doesNotContain("Could not pass event WorldLoadEvent");
    }
}
