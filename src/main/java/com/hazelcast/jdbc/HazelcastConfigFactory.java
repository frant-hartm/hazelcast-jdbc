/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.jdbc;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.client.properties.ClientProperty;
import com.hazelcast.config.SSLConfig;
import com.hazelcast.security.UsernamePasswordCredentials;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class HazelcastConfigFactory {

    private static final Map<String, BiConsumer<ClientConfig, String>> CONFIGURATION_MAPPING;

    static {
        Map<String, BiConsumer<ClientConfig, String>> map = new HashMap<>();
        map.put("clusterName", ClientConfig::setClusterName);
        gcpConfigMapping(map);
        awsConfigMapping(map);
        sslConfigMapping(map);
        k8sConfigMapping(map);
        CONFIGURATION_MAPPING = Collections.unmodifiableMap(map);
    }

    private static void k8sConfigMapping(Map<String, BiConsumer<ClientConfig, String>> map) {
        map.put("k8sServiceDns", (c, p) -> k8sConfig(c, "service-dns", p));
        map.put("k8sServicePort", (c, p) -> k8sConfig(c, "service-port", p));
    }

    private static void sslConfigMapping(Map<String, BiConsumer<ClientConfig, String>> map) {
        map.put("sslEnabled", (c, p) -> sslConfig(c, (ssl) -> ssl.setEnabled(p.equalsIgnoreCase("true"))));
        map.put("trustStore", (c, p) -> sslConfig(c, "trustStore", p));
        map.put("trustStorePassword", (c, p) -> sslConfig(c, "trustStorePassword", p));
        map.put("protocol", (c, p) -> sslConfig(c, "protocol", p));
        map.put("trustCertCollectionFile", (c, p) -> sslConfig(c, "trustCertCollectionFile", p));
        map.put("keyFile", (c, p) -> sslConfig(c, "keyFile", p));
        map.put("keyCertChainFile", (c, p) -> sslConfig(c, "keyCertChainFile", p));
        map.put("factoryClassName", (c, p) -> sslConfig(c, (ssl) -> ssl.setFactoryClassName(p)));
    }

    private static void awsConfigMapping(Map<String, BiConsumer<ClientConfig, String>> map) {
        map.put("awsTagKey", (c, v) -> awsConfig(c, "tag-key", v));
        map.put("awsTagValue", (c, v) -> awsConfig(c, "tag-value", v));
        map.put("awsAccessKey", (c, v) -> awsConfig(c, "access-key", v));
        map.put("awsSecretKey", (c, v) -> awsConfig(c, "secret-key", v));
        map.put("awsIamRole", (c, v) -> awsConfig(c, "iam-role", v));
        map.put("awsRegion", (c, v) -> awsConfig(c, "region", v));
        map.put("awsHostHeader", (c, v) -> awsConfig(c, "host-header", v));
        map.put("awsSecurityGroupName", (c, v) -> awsConfig(c, "security-group-name", v));
        map.put("awsConnectionTimeoutSeconds", (c, v) -> awsConfig(c, "connection-timeout-seconds", v));
        map.put("awsReadTimeoutSeconds", (c, v) -> awsConfig(c, "read-timeout-seconds", v));
        map.put("awsConnectionRetries", (c, v) -> awsConfig(c, "connection-retries", v));
        map.put("awsHzPort", (c, v) -> awsConfig(c, "hz-port", v));
        map.put("awsUsePublicIp", (c, v) -> c.getNetworkConfig()
                .getAwsConfig().setEnabled(true).setUsePublicIp(v.equalsIgnoreCase("true")));
    }

    private static void gcpConfigMapping(Map<String, BiConsumer<ClientConfig, String>> map) {
        map.put("gcpPrivateKeyPath", (c, v) -> gcpConfig(c, "private-key-path", v));
        map.put("gcpHzPort", (c, v) -> gcpConfig(c, "hz-port", v));
        map.put("gcpProjects", (c, v) -> gcpConfig(c, "projects", v));
        map.put("gcpRegion", (c, v) -> gcpConfig(c, "region", v));
        map.put("gcpLabel", (c, v) -> gcpConfig(c, "label", v));
        map.put("gcpUsePublicIp", (c, v) -> c.getNetworkConfig()
                .getGcpConfig().setEnabled(true)
                .setUsePublicIp(v.equalsIgnoreCase("true")).setProperty("use-public-ip", "true"));
    }

    ClientConfig clientConfig(JdbcUrl url) {
        ClientConfig clientConfig = securityConfig(url, ClientConfig.load());
        String discoverToken = url.getProperties().getProperty("discoverToken");
        if (discoverToken != null) {
            return cloudConfig(url, clientConfig, discoverToken);
        }
        ClientNetworkConfig networkConfig = new ClientNetworkConfig().addAddress(url.getAuthority());
        clientConfig.setNetworkConfig(networkConfig);

        CONFIGURATION_MAPPING.forEach((k, v) -> {
            String property = url.getProperties().getProperty(k);
            if (property != null) {
                try {
                    property = URLDecoder.decode(property, StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException ignored) {
                }
                v.accept(clientConfig, property);
            }
        });
        return clientConfig;
    }

    private ClientConfig securityConfig(JdbcUrl url, ClientConfig clientConfig) {
        String user = url.getProperties().getProperty("user");
        String password = url.getProperties().getProperty("password");
        if (user != null || password != null) {
            clientConfig.getSecurityConfig().setCredentials(new UsernamePasswordCredentials(user, password));
        }
        return clientConfig;
    }

    private ClientConfig cloudConfig(JdbcUrl url, ClientConfig clientConfig, String discoverToken) {
        clientConfig.setProperty(ClientProperty.HAZELCAST_CLOUD_DISCOVERY_TOKEN.getName(), discoverToken);
        clientConfig.setClusterName(url.getAuthority());
        return clientConfig;
    }

    private static void k8sConfig(ClientConfig clientConfig, String property, String value) {
        clientConfig.getNetworkConfig().getKubernetesConfig()
                .setEnabled(true)
                .setProperty(property, value);
    }

    private static void sslConfig(ClientConfig clientConfig, String property, String value) {
        SSLConfig sslConfig = clientConfig.getNetworkConfig().getSSLConfig();
        if (sslConfig == null) {
            sslConfig = new SSLConfig();
        }
        sslConfig.setProperty(property, value);
        clientConfig.getNetworkConfig().setSSLConfig(sslConfig);
    }

    private static void sslConfig(ClientConfig clientConfig, Consumer<SSLConfig> sslConfigFunction) {
        SSLConfig sslConfig = clientConfig.getNetworkConfig().getSSLConfig();
        if (sslConfig == null) {
            sslConfig = new SSLConfig();
        }
        sslConfigFunction.accept(sslConfig);
        clientConfig.getNetworkConfig().setSSLConfig(sslConfig);
    }

    private static void awsConfig(ClientConfig clientConfig, String property, String value) {
        clientConfig.getNetworkConfig()
                .getAwsConfig()
                .setEnabled(true)
                .setProperty(property, value);
    }

    private static void gcpConfig(ClientConfig clientConfig, String property, String value) {
        clientConfig.getNetworkConfig()
                .getGcpConfig()
                .setEnabled(true)
                .setProperty(property, value);
    }
}
