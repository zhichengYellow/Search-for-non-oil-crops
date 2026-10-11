package org.nonfood.oilplants.config;

import java.sql.Connection;
import java.sql.SQLException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class Database {
    private static volatile HikariDataSource pool;
    private Database() { }
    public static Connection getConnection() throws SQLException {
        return dataSource().getConnection();
    }
    private static HikariDataSource dataSource() throws SQLException {
        HikariDataSource current=pool;
        if(current!=null&&!current.isClosed())return current;
        synchronized(Database.class){
            current=pool;
            if(current!=null&&!current.isClosed())return current;
            String url=System.getenv("DB_URL"),user=System.getenv("DB_USER"),password=System.getenv("DB_PASSWORD");
            if(url==null||user==null||password==null)throw new SQLException("Database is not configured. Set DB_URL, DB_USER and DB_PASSWORD.");
            int maxPoolSize=positiveEnv("DB_POOL_MAX_SIZE",10,100);
            int minIdle=Math.min(positiveEnv("DB_POOL_MIN_IDLE",2,maxPoolSize),maxPoolSize);
            long timeout=positiveEnv("DB_POOL_CONNECTION_TIMEOUT_MS",30000,120000);
            if(timeout<250)throw new IllegalArgumentException("DB_POOL_CONNECTION_TIMEOUT_MS must be at least 250.");
            HikariConfig config=new HikariConfig();config.setJdbcUrl(url);config.setUsername(user);config.setPassword(password);
            config.setPoolName("PlantScopeMySQL");config.setMaximumPoolSize(maxPoolSize);config.setMinimumIdle(minIdle);
            config.setConnectionTimeout(timeout);config.setValidationTimeout(Math.min(timeout,5000));config.setMaxLifetime(1_800_000);
            config.setAutoCommit(true);config.setInitializationFailTimeout(-1);
            pool=new HikariDataSource(config);return pool;
        }
    }
    private static int positiveEnv(String name,int fallback,int max){
        String value=System.getenv(name);if(value==null||value.isBlank())return fallback;
        try{int parsed=Integer.parseInt(value);if(parsed<1||parsed>max)throw new NumberFormatException();return parsed;}
        catch(NumberFormatException e){throw new IllegalArgumentException(name+" must be an integer from 1 to "+max+".");}
    }
    public static void shutdown(){synchronized(Database.class){if(pool!=null){pool.close();pool=null;}}}
    static HikariDataSource currentPool(){return pool;}
}
