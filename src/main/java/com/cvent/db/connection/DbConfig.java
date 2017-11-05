package com.cvent.db.connection;

/**
 * @author : a.srivastava
 **/
public class DbConfig {
    private final String driver;
    private final String url;
    private final String username;
    private final String password;

    public DbConfig(String driver, String url, String username, String password) {
        this.driver = driver;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public String getDriver() {
        return driver;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
