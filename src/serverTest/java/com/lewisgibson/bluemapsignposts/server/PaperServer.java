package com.lewisgibson.bluemapsignposts.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

/**
 * One throwaway Paper 26.3 server in Docker with BlueMap, the built plugin and the fixture
 * installed. The image is itzg/minecraft-server, pinned by digest, on a small heap; BlueMap is
 * installed by the image from Modrinth at the version the plugin's BlueMapAPI pin was verified
 * against. The first run downloads the image, the Paper jar, BlueMap, the JDBC libraries and
 * BlueMap's Minecraft resources, so it is slow once and quick after.
 */
final class PaperServer implements AutoCloseable {

    static final String IMAGE =
            "itzg/minecraft-server:java25@sha256:03d12ffd613cb085e4285c0dc1e2b7eaa92cb09422d3820809314f9b7ae0dd4a";
    static final String BLUEMAP = "bluemap:5.29-paper";
    static final String RCON_PASSWORD = "server-test";

    private final Path data;
    private String container;
    private int rconPort;

    PaperServer(Path data) {
        this.data = data;
    }

    /** Writes the plugin config the test wants and installs both jars, before the server starts. */
    void install(Path pluginJar, Path fixtureJar, String pluginConfigYaml) throws IOException {
        Path plugins = data.resolve("plugins");
        Files.createDirectories(plugins.resolve("BlueMapSignPosts"));
        Files.createDirectories(plugins.resolve("BlueMap"));
        Files.copy(
                pluginJar,
                plugins.resolve(pluginJar.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(
                fixtureJar,
                plugins.resolve(fixtureJar.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(plugins.resolve("BlueMapSignPosts/config.yml"), pluginConfigYaml);
        // BlueMap will not load its maps (and so never enables its API) until the download of
        // Mojang's resources has been accepted; one render thread keeps the flat world cheap.
        Files.writeString(
                plugins.resolve("BlueMap/core.conf"),
                "accept-download: true\nrender-thread-count: 1\nmetrics: false\n");
    }

    /** Overwrites the plugin's config on the running server, for a live reload. */
    void writeConfig(String pluginConfigYaml) throws IOException {
        Files.writeString(data.resolve("plugins/BlueMapSignPosts/config.yml"), pluginConfigYaml);
    }

    /** Boots the server and waits for "Done"; returns the log so far. */
    String start() {
        // The image runs the server as UID 1000 unless told otherwise, and the harness rewrites the
        // plugin's config.yml in the mounted folder mid-test; on a host where the test runs as
        // another user (GitHub's runners are 1001) that write is refused. Run as the owner of the
        // data folder instead.
        String uid;
        String gid;
        try {
            uid = Files.getAttribute(data, "unix:uid").toString();
            gid = Files.getAttribute(data, "unix:gid").toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        container =
                Docker.start(
                        List.of(
                                "-e",
                                "UID=" + uid,
                                "-e",
                                "GID=" + gid,
                                "-e",
                                "EULA=TRUE",
                                "-e",
                                "TYPE=PAPER",
                                "-e",
                                "VERSION=26.3",
                                "-e",
                                "ONLINE_MODE=FALSE",
                                "-e",
                                "MEMORY=2G",
                                "-e",
                                "MAX_TICK_TIME=-1",
                                "-e",
                                "SPAWN_PROTECTION=0",
                                "-e",
                                "LEVEL_TYPE=FLAT",
                                "-e",
                                "VIEW_DISTANCE=2",
                                "-e",
                                "MODRINTH_PROJECTS=" + BLUEMAP,
                                "-e",
                                "ENABLE_RCON=TRUE",
                                "-e",
                                "RCON_PASSWORD=" + RCON_PASSWORD,
                                "-p",
                                "127.0.0.1::25575",
                                "-v",
                                data.toAbsolutePath() + ":/data",
                                IMAGE));
        String logs =
                Docker.awaitLogs(
                        container,
                        text ->
                                text.contains("Done (")
                                        || text.contains(
                                                "Error occurred while enabling BlueMapSignPosts"),
                        Duration.ofMinutes(15),
                        "Paper to finish starting");
        rconPort = Docker.hostPort(container, 25575);
        return logs;
    }

    /**
     * Waits until the plugin has logged that BlueMap came up and it published, the n-th time; the
     * first is on start, later ones after {@code /bluemap reload}.
     */
    String awaitPublished(int times) {
        return Docker.awaitLogs(
                container,
                text -> count(text, "BlueMap is up: published") >= times,
                Duration.ofMinutes(5),
                "BlueMap to enable and the plugin to publish (" + times + ")");
    }

    private static int count(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    String logs() {
        return Docker.logs(container);
    }

    /** Runs one command over RCON and returns the reply text. */
    String rcon(String command) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            try (Rcon rcon = new Rcon("127.0.0.1", rconPort, RCON_PASSWORD)) {
                // Replies carry a trailing newline; the tests compare the words.
                return rcon.command(command).trim();
            } catch (IOException ex) {
                last = ex;
                Docker.sleep(Duration.ofSeconds(2));
            }
        }
        throw last;
    }

    /**
     * Repeats an RCON command until its reply matches, for something the server does a tick later.
     */
    String awaitRcon(String command, java.util.function.Predicate<String> ready)
            throws IOException {
        String reply = "";
        for (int attempt = 0; attempt < 30; attempt++) {
            reply = rcon(command);
            if (ready.test(reply)) {
                return reply;
            }
            Docker.sleep(Duration.ofSeconds(1));
        }
        throw new IllegalStateException("'" + command + "' never got there; last reply: " + reply);
    }

    @Override
    public void close() {
        if (container != null) {
            Docker.stop(container);
        }
    }
}
