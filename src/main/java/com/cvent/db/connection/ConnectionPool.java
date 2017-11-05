package com.cvent.db.connection;

import java.sql.SQLException;
/**
 * Interface for a basic Connection Pool.
 * 
 * @author : a.srivastava
 **/
public interface ConnectionPool {
        /**
         * Gets a connection from the connection pool.
         *
         * @return a valid connection from the pool.
         */
        ConnectionInstance getConnection() throws SQLException;

        /**
         * Releases a connection back into the connection pool.
         *
         * @param connection the connection to return to the pool
         * @throws java.sql.SQLException
         */
        void releaseConnection(ConnectionInstance connection) throws SQLException;
}
