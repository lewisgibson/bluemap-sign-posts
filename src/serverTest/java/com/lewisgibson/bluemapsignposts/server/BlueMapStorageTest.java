package com.lewisgibson.bluemapsignposts.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The plugin never reads or writes BlueMap's storage itself: it only puts markers into BlueMap's
 * marker sets through BlueMapAPI, and BlueMap saves them into whatever storage each map uses. This
 * proves that on one real server by moving the maps through every kind of BlueMap storage in turn
 * (the default files, SQLite, PostgreSQL with TLS required and the certificate verified, and
 * MariaDB) with {@code /bluemap reload}, and after each move reading BlueMap's saved marker state
 * back out of that storage and finding the signpost in it.
 */
@DisplayName("BlueMapSignPosts on every BlueMap storage")
class BlueMapStorageTest {

    private static final Path PLUGIN_JAR = Path.of(System.getProperty("bmsp.pluginJar"));
    private static final Path FIXTURE_JAR = Path.of(System.getProperty("bmsp.fixtureJar"));
    private static final Path WORK = Path.of(System.getProperty("bmsp.workDir"));
    private static final List<Path> DRIVERS =
            Stream.of(System.getProperty("bmsp.blueMapDrivers").split(java.io.File.pathSeparator))
                    .map(Path::of)
                    .toList();
    private static final long ONE_HOUR = 72_000L;

    @BeforeAll
    static void requireDocker() {
        Docker.requireDocker();
    }

    @Test
    @DisplayName("file, SQLite, PostgreSQL over verified TLS and MariaDB")
    void everyStorage() throws Exception {
        Path data = PaperServerTest.freshDirectory(WORK.resolve("bluemap-storages"));
        try (PostgresServer postgres = new PostgresServer();
                MySqlServer mariadb = new MySqlServer(MySqlServer.Flavour.MARIADB);
                PaperServer paper = new PaperServer(data)) {
            postgres.start();
            postgres.requireTls(WORK.resolve("bluemap-storages-tls"));
            mariadb.start();

            paper.install(PLUGIN_JAR, FIXTURE_JAR, "storage:\n    type: sqlite\n");
            Path blueMap = data.resolve("plugins/BlueMap");
            for (Path driver : DRIVERS) {
                Files.copy(driver, blueMap.resolve(driver.getFileName()));
            }
            Files.copy(postgres.caCertificate(), blueMap.resolve("ca.crt"));

            assertThat(paper.start()).doesNotContain("Error occurred while enabling");
            paper.awaitPublished(1);

            // The default: file storage.
            sign(paper, 0, "Files");
            paper.rcon("bluemap reload");
            paper.awaitPublished(2);
            assertThat(fileMarkers(data)).contains("Files");

            // SQLite, PostgreSQL over TLS with the certificate and host checked, and MariaDB.
            String sqlite =
                    sqlStorage(
                            "jdbc:sqlite:plugins/BlueMap/bluemap.db",
                            driver("sqlite-jdbc"),
                            "org.sqlite.JDBC",
                            "",
                            "");
            String postgresTls =
                    sqlStorage(
                            "jdbc:postgresql://"
                                    + postgres.bridgeAddress()
                                    + ":5432/"
                                    + PostgresServer.DATABASE
                                    + "?sslmode=verify-full&sslrootcert=plugins/BlueMap/ca.crt",
                            driver("postgresql"),
                            "org.postgresql.Driver",
                            PostgresServer.USER,
                            PostgresServer.PASSWORD);
            String mariaDb =
                    sqlStorage(
                            "jdbc:mariadb://"
                                    + mariadb.bridgeAddress()
                                    + ":3306/"
                                    + MySqlServer.DATABASE,
                            driver("mariadb-java-client"),
                            "org.mariadb.jdbc.Driver",
                            MySqlServer.USER,
                            MySqlServer.PASSWORD);

            useStorage(paper, blueMap, "sqlite", sqlite, 3);
            sign(paper, 2, "In SQLite");
            useStorage(paper, blueMap, "postgres", postgresTls, 4);
            assertThat(sqliteMarkers(data.resolve("plugins/BlueMap/bluemap.db")))
                    .contains("In SQLite");

            sign(paper, 4, "In PostgreSQL");
            useStorage(paper, blueMap, "mariadb", mariaDb, 5);
            assertThat(
                            decodeAll(
                                    postgres.query(
                                            "SELECT translate(encode(d.data, 'base64'), E'\\n', '') FROM"
                                                    + " bluemap_item_storage_data d JOIN"
                                                    + " bluemap_item_storage s ON s.id = d.storage"
                                                    + " WHERE s.key LIKE '%markers%'")))
                    .contains("In PostgreSQL");

            sign(paper, 6, "In MariaDB");
            paper.rcon("bluemap reload");
            paper.awaitPublished(6);
            assertThat(
                            decodeAll(
                                    mariadb.query(
                                            "SELECT REPLACE(TO_BASE64(d.data), '\\n', '') FROM"
                                                    + " bluemap_item_storage_data d JOIN"
                                                    + " bluemap_item_storage s ON s.id = d.storage"
                                                    + " WHERE s.key LIKE '%markers%'")))
                    .contains("In MariaDB");

            String logs = paper.logs();
            assertThat(logs)
                    .contains("Initializing Storage: 'sqlite'")
                    .contains("Initializing Storage: 'postgres'")
                    .contains("Initializing Storage: 'mariadb'")
                    .doesNotContain("A store write failed");
        }
    }

    /** A signpost at x through the real event pipeline, already on BlueMap's marker set. */
    private static void sign(PaperServer paper, int x, String label) throws Exception {
        assertThat(
                        paper.rcon(
                                "bmsptest sign "
                                        + x
                                        + " 64 0 "
                                        + ONE_HOUR
                                        + " bypass [map]|"
                                        + label
                                        + "||"))
                .contains("marker=" + label);
    }

    private static String sqlStorage(
            String url, Path driverJar, String driverClass, String user, String password) {
        return """
                storage-type: sql
                connection-url: "%s"
                connection-properties: {
                  user: "%s",
                  password: "%s"
                }
                max-connections: -1
                driver-jar: "plugins/BlueMap/%s"
                driver-class: "%s"
                compression: gzip
                """
                .formatted(url, user, password, driverJar.getFileName(), driverClass);
    }

    private static Path driver(String artifact) {
        return DRIVERS.stream()
                .filter(path -> path.getFileName().toString().startsWith(artifact + "-"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no driver jar for " + artifact));
    }

    /**
     * Writes the storage config, points every map at it, reloads BlueMap and waits for the plugin
     * to publish again; the unload that a reload starts with saves the old storage's marker state.
     */
    private static void useStorage(
            PaperServer paper, Path blueMap, String id, String config, int published)
            throws IOException {
        Files.writeString(blueMap.resolve("storages/" + id + ".conf"), config);
        try (Stream<Path> maps = Files.list(blueMap.resolve("maps"))) {
            for (Path map : maps.toList()) {
                String text = Files.readString(map);
                Files.writeString(
                        map, text.replaceAll("(?m)^storage: .*$", "storage: \"" + id + "\""));
            }
        }
        paper.rcon("bluemap reload");
        paper.awaitPublished(published);
    }

    /** Every saved marker file under the default file storage, decoded, joined. */
    private static String fileMarkers(Path data) throws IOException {
        StringBuilder out = new StringBuilder();
        try (Stream<Path> files = Files.walk(data.resolve("bluemap/web/maps"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (file.toString().contains("markers")) {
                    out.append(decode(Files.readAllBytes(file)));
                }
            }
        }
        return out.toString();
    }

    private static String sqliteMarkers(Path database) throws Exception {
        List<String> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT d.data FROM bluemap_item_storage_data d JOIN"
                                        + " bluemap_item_storage s ON s.id = d.storage WHERE s.key"
                                        + " LIKE '%markers%'")) {
            while (result.next()) {
                rows.add(decode(result.getBytes(1)));
            }
        }
        return String.join("\n", rows);
    }

    /** One base64 value per line, as the queries above print them. */
    private static String decodeAll(String base64Rows) {
        StringBuilder out = new StringBuilder();
        for (String row : base64Rows.strip().split("\\R")) {
            if (!row.isBlank()) {
                out.append(decode(Base64.getDecoder().decode(row.strip())));
            }
        }
        return out.toString();
    }

    /** BlueMap compresses what it stores; gzip is the default and the one the test configures. */
    private static String decode(byte[] bytes) {
        if (bytes.length > 1 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
