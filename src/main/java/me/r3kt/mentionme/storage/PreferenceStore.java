package me.r3kt.mentionme.storage;

import me.r3kt.mentionme.model.Preferences;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;

public final class PreferenceStore {
    private final File databaseFile;
    private final ExecutorService executor;
    private Connection connection;

    public PreferenceStore(File databaseFile) {
        this.databaseFile = databaseFile;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MentionMe-Storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    statement.execute("PRAGMA synchronous=NORMAL");
                    statement.execute("PRAGMA busy_timeout=5000");
                    statement.execute("""
                            CREATE TABLE IF NOT EXISTS preferences(
                              uuid TEXT PRIMARY KEY,
                              mentions INTEGER NOT NULL,
                              sound INTEGER NOT NULL,
                              actionbar INTEGER NOT NULL,
                              title INTEGER NOT NULL
                            )
                            """);
                }
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Preferences> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT mentions,sound,actionbar,title FROM preferences WHERE uuid=?")) {
                statement.setString(1, uuid.toString());

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return new Preferences(
                                result.getInt("mentions") == 1,
                                result.getInt("sound") == 1,
                                result.getInt("actionbar") == 1,
                                result.getInt("title") == 1
                        );
                    }
                }

                return Preferences.defaults();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> save(UUID uuid, Preferences preferences) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO preferences(uuid,mentions,sound,actionbar,title)
                    VALUES(?,?,?,?,?)
                    ON CONFLICT(uuid) DO UPDATE SET
                      mentions=excluded.mentions,
                      sound=excluded.sound,
                      actionbar=excluded.actionbar,
                      title=excluded.title
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, preferences.mentions() ? 1 : 0);
                statement.setInt(3, preferences.sound() ? 1 : 0);
                statement.setInt(4, preferences.actionbar() ? 1 : 0);
                statement.setInt(5, preferences.title() ? 1 : 0);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> delete(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM preferences WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public void shutdown() {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
