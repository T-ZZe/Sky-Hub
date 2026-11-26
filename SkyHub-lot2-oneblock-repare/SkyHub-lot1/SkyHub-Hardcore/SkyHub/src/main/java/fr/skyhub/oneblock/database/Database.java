package fr.skyhub.oneblock.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.skyhub.oneblock.OneBlockPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Accès SQL via HikariCP. Toutes les opérations exécutées dans une transaction ; versions async (hors thread principal) et sync (démarrage/arrêt). */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface SqlWork<T> { T run(Connection c) throws SQLException; }

    private final HikariDataSource ds;
    private final ExecutorService executor;
    private final boolean mysql;

    public Database(OneBlockPlugin plugin) {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("database");
        String type = cfg == null ? "sqlite" : cfg.getString("type", "sqlite").toLowerCase();
        this.mysql = type.equals("mysql");
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("OneBlockRPG-Pool");
        if (mysql) {
            ConfigurationSection m = cfg.getConfigurationSection("mysql");
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hc.setJdbcUrl("jdbc:mysql://" + m.getString("host", "localhost") + ":" + m.getInt("port", 3306) + "/" + m.getString("database", "oneblock")
                    + "?useSSL=" + m.getBoolean("use-ssl", false) + "&characterEncoding=utf8&allowPublicKeyRetrieval=true");
            hc.setUsername(m.getString("user", "root"));
            hc.setPassword(m.getString("password", ""));
            hc.setMaximumPoolSize(Math.max(2, m.getInt("pool-size", 8)));
        } else {
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setJdbcUrl("jdbc:sqlite:" + new File(plugin.getDataFolder(), "oneblock.db").getAbsolutePath());
            hc.setMaximumPoolSize(1); // SQLite : un seul écrivain
            hc.setConnectionInitSql("PRAGMA busy_timeout=5000");
        }
        this.ds = new HikariDataSource(hc);
        this.executor = Executors.newFixedThreadPool(mysql ? Math.max(2, hc.getMaximumPoolSize() / 2) : 1, r -> {
            Thread t = new Thread(r, "OneBlockRPG-SQL");
            t.setDaemon(true);
            return t;
        });
    }

    public boolean isMysql() { return mysql; }

    /** Crée les tables (les nouveaux modules ajouteront les leurs ici). */
    public void createSchema() throws SQLException {
        sync(c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS islands (id INT PRIMARY KEY, owner VARCHAR(36) NOT NULL, slot INT NOT NULL, name VARCHAR(64) NOT NULL,"
                        + " blocks BIGINT NOT NULL, phase INT NOT NULL, rewarded_phase INT NOT NULL, mobs_killed BIGINT NOT NULL, created BIGINT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS island_members (island_id INT NOT NULL, uuid VARCHAR(36) NOT NULL, name VARCHAR(16) NOT NULL,"
                        + " role VARCHAR(16) NOT NULL, joined BIGINT NOT NULL, contributed BIGINT NOT NULL, PRIMARY KEY (island_id, uuid))");
            }
            return null;
        });
    }

    public <T> T sync(SqlWork<T> work) throws SQLException {
        try (Connection c = ds.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T r = work.run(c);
                c.commit();
                return r;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        }
    }

    public <T> CompletableFuture<T> async(SqlWork<T> work) {
        CompletableFuture<T> f = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try { f.complete(sync(work)); } catch (Throwable t) { f.completeExceptionally(t); }
            });
        } catch (RuntimeException rejected) {
            f.completeExceptionally(rejected);
        }
        return f;
    }

    @Override public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        ds.close();
    }
}
