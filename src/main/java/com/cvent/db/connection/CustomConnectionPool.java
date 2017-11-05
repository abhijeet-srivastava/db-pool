package com.cvent.db.connection;

import java.sql.Connection;
import java.util.List;

/**
 * Custom Interface for the basic Connection Pool.
 * 
 * @author : a.srivastava
 **/
public interface CustomConnectionPool extends ConnectionPool {
    /**
     * Method to calculate number of available connections in the pool.
     * @return number of available connections.
     * @param availableConnections
     */
    public int getNumberOfAvailableConnections(List<Connection> availableConnections);

    /**
     * Method to calculate number of busy connections in the pool.
     * @return number of busy connections.
     * @param busyConnections
     */
    public int getNumberOfBusyConnections(List<Connection> busyConnections);

    /**
     * Method to close all the connections.
     */
    public void closeAllConnections();
}
