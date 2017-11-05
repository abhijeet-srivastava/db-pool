package com.cvent.db.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a sample implementation of the connection pool.
 * 
 * @author : a.srivastava
 **/
public class CustomConnectionPoolImpl implements  CustomConnectionPool{
    
    private Logger log = LoggerFactory.getLogger(getClass());

    private final DbConfig db1Config;
    private final DbConfig db2Config;
    private int maxConnections;
    private boolean waitIfBusy;
    private List<ConnectionInstance> availableConnections, busyConnections;
    private List<Connection> availableConnectionsDb1, busyConnectionsDb1;
    private List<Connection> availableConnectionsDb2, busyConnectionsDb2;
    private boolean connectionPending = false;

    public CustomConnectionPoolImpl(DbConfig db1Config, DbConfig db2Config,
                                    int initialConnections, int maxConnections,
                                    boolean waitIfBusy) throws SQLException {
        validateConfig(db1Config);
        validateConfig(db2Config);
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("The maximum number of connections must be greater than 0.");
        }
        this.db1Config = db1Config;
        this.db2Config = db2Config;
        this.maxConnections = maxConnections;
        this.waitIfBusy = waitIfBusy;
        if (initialConnections > maxConnections) {
            initialConnections = maxConnections;
        }
        availableConnectionsDb1 = Collections.synchronizedList(new ArrayList<Connection>(initialConnections));
        busyConnectionsDb1 = Collections.synchronizedList(new ArrayList<Connection>());

        availableConnectionsDb2 = Collections.synchronizedList(new ArrayList<Connection>(initialConnections));
        busyConnectionsDb2 = Collections.synchronizedList(new ArrayList<Connection>());
        for (int i = 0; i < initialConnections; i++) {
            availableConnectionsDb1.add(makeNewConnection(this.db1Config));
            availableConnectionsDb2.add(makeNewConnection(this.db2Config));
        }
        log.debug("Available connections: " + availableConnectionsDb1.size());
    }

    private void validateConfig(DbConfig dbConfig) {
        if (dbConfig.getDriver() == null) {
            throw new IllegalArgumentException("The given driver is null.");
        }
        if (dbConfig.getUrl() == null) {
            throw new IllegalArgumentException("The given url is null.");
        }
        if (dbConfig.getUsername() == null || dbConfig.getPassword() == null) {
            throw new IllegalArgumentException(
                    "The username or password is null.");
        }
    }

    public  ConnectionInstance getConnection() throws SQLException {
        Connection connDb1 = getConnectionFromAvailableList(availableConnectionsDb1, busyConnectionsDb1, db1Config);
        Connection connDb2 = getConnectionFromAvailableList(availableConnectionsDb2, busyConnectionsDb2, db2Config);
        return new ConnectionInstance(connDb1, connDb2);
        
    }

    public  Connection getConnectionFromAvailableList(List<Connection> availableConnections,
                                                                  List<Connection> busyConnections,
                                                                  DbConfig dbConfig) throws SQLException {
        synchronized (availableConnections) {
        if (!availableConnections.isEmpty()) {
            int lastIndex = availableConnections.size() - 1;
            Connection existingConnection = (Connection) availableConnections.get(lastIndex);
            availableConnections.remove(lastIndex);
            log.debug("available connections: " + availableConnections.size());

            // If connection on available list is closed (e.g. it timed out), then remove it from available list 
            // and repeat the process of obtaining a connection. Also wake up threads that were waiting for a 
            // connection because maxConnection limit was reached.
            if (existingConnection.isClosed()) {
                notifyAll(); // Freed up a spot for anybody waiting
                return (getConnectionFromAvailableList(availableConnections, busyConnections, dbConfig));
            } else {
                busyConnections.add(existingConnection);
                return (existingConnection);
            }
        } else {
            // Three possible cases:
            // 1) we haven't reached maxConnections limit. So establish one in the background 
            //      if there isn't already one pending, then wait for the next available connection 
            //      (whether or not it was the newly established one).
            // 2) we have reached maxConnections limit and waitIfBusy flag is false.
            //      Throw SQLException in such a case.
            // 3) we have reached maxConnections limit and waitIfBusy flag is true.
            //      Then do the same thing as in second part of step 1: wait for next available connection.
            if (((getNumberOfAvailableConnections(availableConnections) + getNumberOfBusyConnections(busyConnections))
                    < maxConnections) && !connectionPending) {
                makeBackgroundConnection(availableConnections, dbConfig);
            } else if (!waitIfBusy) {
                throw new SQLException("Connection limit reached");
            }

            // Wait for either a new connection to be established (if you called
            // makeBackgroundConnection) or for an existing connection to be
            // freed up.
            try {
                wait();
            } catch (InterruptedException ie) {
            }
            log.debug("available connections: " + availableConnections.size());
        }
        // Someone freed up a connection, so try again.
        return (getConnectionFromAvailableList(availableConnections, busyConnections, dbConfig));
        }
    }
    // we can't just make a new connection in the foreground when none are available, since this can take several 
    // seconds with a slow network connection. Instead, start a thread that establishes a new connection, then wait. 
    // You get woken up either when the new connection is established or if someone finishes with an existing connection.
    private void makeBackgroundConnection(List<Connection> availableConnections, DbConfig dbConfig) {
        connectionPending = true;
        try {
            Thread connectThread = new Thread(new ConnectionCreator(dbConfig, availableConnections));
            connectThread.start();
        } catch (OutOfMemoryError oome) {
            // Give up on new connection
        }
    }

    public class ConnectionCreator implements Runnable {
        private final DbConfig dbConfig;
        private final List<Connection> availableConnections;

        public ConnectionCreator(DbConfig dbConfig, List<Connection> availableConnections) {
            this.dbConfig = dbConfig;
            this.availableConnections = availableConnections;
        }
        public void run() {
            try {
                Connection connection = makeNewConnection(dbConfig);
                synchronized (dbConfig) {
                    availableConnections.add(connection);
                    connectionPending = false;
                    notifyAll();
                }
            } catch (Exception e) { // SQLException or OutOfMemory
                // Give up on new connection and wait for existing one to free up.
            }
        }
    }

    private Connection makeNewConnection(DbConfig dbConfig) throws SQLException {
        synchronized (dbConfig) {
            try {
                // Load database driver if not already loaded
                Class.forName(dbConfig.getDriver());
                // Establish network connection to database
                Connection connection
                        = DriverManager.getConnection(dbConfig.getUrl(), dbConfig.getUsername(), dbConfig.getUsername());
                return (connection);
            } catch (ClassNotFoundException cnfe) {
                // Simplify try/catch blocks of people using this by throwing only
                // one exception type.
                throw new SQLException("Can't find class for driver: " + dbConfig.getUrl());
            }
        }
    }

    // This explicitly makes a new connection. Called in the foreground when
    // initializing the ConnectionPoolImpl, and called in the background when
    // running.
    
    public synchronized void releaseConnection(ConnectionInstance connectionInstance)
            throws SQLException {
        busyConnectionsDb1.remove(connectionInstance.getDb1Connection());
        availableConnectionsDb1.add(connectionInstance.getDb1Connection());

        busyConnectionsDb2.remove(connectionInstance.getDb2Connection());
        availableConnectionsDb2.add(connectionInstance.getDb2Connection());
        // Wake up threads that are waiting for a connection
        notifyAll();
    }

    // Closes all the connections. we need to make sure that no connections are
    // in use before calling.  
    public synchronized void closeAllConnections() {
        closeConnections(availableConnectionsDb1);
        availableConnectionsDb1 = Collections
                .synchronizedList(new ArrayList<Connection>());
        closeConnections(busyConnectionsDb1);
        busyConnectionsDb1 = Collections
                .synchronizedList(new ArrayList<Connection>());
    }

    private void closeConnections(List<Connection> connections) {
        try {
            for (Connection connection : connections) {
                if (!connection.isClosed()) {
                    connection.close();
                }
            }
        } catch (SQLException sqle) {
            // Ignore errors; garbage collect anyhow
        }
    }

    public  int getNumberOfAvailableConnections(List<Connection> availableConnections) {
        synchronized (availableConnections) {
            return availableConnections.size();
        }
    }

    public  int getNumberOfBusyConnections(List<Connection> busyConnections) {
        synchronized (busyConnections) {
            return busyConnections.size();
        }
    }

    @Override
    public synchronized String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Class: ").append(this.getClass().getName()).append("\n");
        result.append(" available: ").append('\n')
                .append("DB1 : ").append(availableConnectionsDb1.size()).append("\n")
                .append("DB2 : ").append(availableConnectionsDb2.size()).append("\n");
        result.append(" busy: ").append('\n')
                .append("DB1 : ").append(busyConnectionsDb1.size()).append("\n")
                .append("DB2 : ").append(busyConnectionsDb2.size()).append("\n");
        result.append(" max: ").append(maxConnections).append("\n");
        return result.toString();
    }
}
