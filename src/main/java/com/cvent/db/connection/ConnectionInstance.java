package com.cvent.db.connection;

import java.sql.Connection;

/**
 * @author : a.srivastava
 **/
public class ConnectionInstance {
    
    private final Connection db1Connection;
    private final Connection db2Connection;

    public ConnectionInstance(Connection db1Connection, Connection db2Connection) {
        this.db1Connection = db1Connection;
        this.db2Connection = db2Connection;
    }

    public Connection getDb1Connection() {
        return db1Connection;
    }

    public Connection getDb2Connection() {
        return db2Connection;
    }
}
