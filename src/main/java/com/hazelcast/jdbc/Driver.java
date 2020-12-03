package com.hazelcast.jdbc;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.core.HazelcastInstance;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Logger;

public class Driver implements java.sql.Driver {

    private static final String JDBC_URL_PREFIX = "jdbc:";
    private static final String URL_PREFIX = "jdbc:hazelcast://";

    /** Major version. */
    private static final int VER_MAJOR = 1;

    /** Minor version. */
    private static final int VER_MINOR = 0;

    private static final Driver INSTANCE = new Driver();
    private static boolean registered;

    static {
        load();
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        URI uri = pareUrl(url);
        if (uri == null) {
            throw new SQLException("URL " + url + " is not supported");
        }
        ClientNetworkConfig networkConfig = new ClientNetworkConfig().setAddresses(Collections.singletonList(uri.getAuthority()));
        ClientConfig clientConfig = new ClientConfig().setNetworkConfig(networkConfig);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        return new JdbcConnection(client);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return pareUrl(url) != null;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return VER_MAJOR;
    }

    @Override
    public int getMinorVersion() {
        return VER_MINOR;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("The driver does not use java.util.logging");
    }

    private URI pareUrl(String url) throws SQLException {
        if (url == null) {
            throw new SQLException("URL cannot be null.");
        }
        if (!url.toLowerCase().startsWith(URL_PREFIX)) {
            return null;
        }
        return URI.create(url.substring(JDBC_URL_PREFIX.length()));
    }

    private static synchronized void load() {
        try {
            if (!registered) {
                DriverManager.registerDriver(INSTANCE);
                registered = true;
            }
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
