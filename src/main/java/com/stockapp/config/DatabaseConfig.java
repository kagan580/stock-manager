package com.stockapp.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConfig {

    private static final String HOST = "";// host'u girinn
    private static final String DB   = "neondb";
    private static final String USER = ""; // user'ı girin
    private static final String PASS = ""; // şimdilik sabit

    // ⚠️ socketTimeout=10 çok düşük -> küçük gecikmede bile kopma hissi verir
    private static final String URL =
            "jdbc:postgresql://" + HOST + ":5432/" + DB
                    + "?sslmode=require"
                    + "&connectTimeout=10"
                    + "&socketTimeout=30"
                    + "&loginTimeout=10"
                    + "&tcpKeepAlive=true"
                    // pgjdbc cache (ufak hız artışı)
                    + "&preparedStatementCacheQueries=256"
                    + "&preparedStatementCacheSizeMiB=8";

    private static HikariDataSource ds;

    /** Uygulama açılırken 1 kez çağır (istersen çağırma, ilk getConnection'da zaten init olur) */
    public static synchronized void initPool() {
        if (ds != null) return;

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(URL);
        cfg.setUsername(USER);
        cfg.setPassword(PASS);

        cfg.setConnectionInitSql("SET TIME ZONE 'Europe/Istanbul'");
        // Masaüstü app için küçük pool yeterli
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);

        cfg.setConnectionTimeout(10_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);

        // Idle kalınca kopma/uyku hissini azaltır
        cfg.setKeepaliveTime(300_000);

        // Uygulama açılışında havuzu hızlı hazırlar (hata varsa hızlı yakalar)
        cfg.setInitializationFailTimeout(10_000);

        cfg.setConnectionTestQuery("SELECT 1");
        cfg.setValidationTimeout(3_000);
        cfg.setKeepaliveTime(120_000); // 2 dk'da bir ping (uykudan sonra hızlı toparlar)
        cfg.setMaxLifetime(900_000);   // 15 dk (Neon/proxy için iyi)
        cfg.setIdleTimeout(300_000);   // 5 dk

        ds = new HikariDataSource(cfg);
    }

    public static Connection getAppConnection() {
        try {
            if (ds == null) initPool();
            return ds.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Neon DB bağlantı hatası", e);
        }
    }

    public static Connection getAdminConnection() {
        return getAppConnection();
    }

    public static boolean canConnect() {
        try (Connection c = getAppConnection()) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Uygulama kapanırken çağır */
    public static synchronized void shutdownPool() {
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }
}
