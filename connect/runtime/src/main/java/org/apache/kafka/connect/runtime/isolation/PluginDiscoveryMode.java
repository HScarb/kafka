/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.connect.runtime.isolation;

import java.util.Locale;

/**
 * Connect 插件发现策略
 * Strategy to use to discover plugins usable on a Connect worker.
 * @see <a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-898%3A+Modernize+Connect+plugin+discovery">KIP-898</a>
 */
public enum PluginDiscoveryMode {

    /**
     * 仅通过反射扫描插件，这对应于 KIP-898 之前的 Connect 的传统行为。
     * 不需要插件在编译时显式声明，只要插件类在类路径中可以用，反射就可以找到它们。
     * Scan for plugins reflectively. This corresponds to the legacy behavior of Connect prior to KIP-898.
     * <p>Note: the following plugins are still loaded using {@link java.util.ServiceLoader} in this mode:
     * <ul>
     *     <li>{@link org.apache.kafka.common.config.provider.ConfigProvider}</li>
     *     <li>{@link org.apache.kafka.connect.rest.ConnectRestExtension}</li>
     *     <li>{@link org.apache.kafka.connect.connector.policy.ConnectorClientConfigOverridePolicy}</li>
     * </ul>
     */
    ONLY_SCAN,
    /**
     * 默认的模式。通过反射和 ServiceLoader 扫描插件。如果有插件无法通过 ServiceLoader 使用，则发出警告。即兼容反射模式，
     * Scan for plugins reflectively and via {@link java.util.ServiceLoader}.
     * Emit warnings if one or more plugins is not available via {@link java.util.ServiceLoader}
     */
    HYBRID_WARN,
    /**
     * 通过反射和 ServiceLoader 扫描插件。如果有插件无法通过 ServiceLoader 使用，则在启动期间失败。
     * Scan for plugins reflectively and via {@link java.util.ServiceLoader}.
     * Fail worker during startup if one or more plugins is not available via {@link java.util.ServiceLoader}
     */
    HYBRID_FAIL,
    /**
     * 仅通过 ServiceLoader 发现插件。
     * Discover plugins via {@link java.util.ServiceLoader} only.
     * Plugins may not be usable if they are not available via {@link java.util.ServiceLoader}
     */
    SERVICE_LOAD;

    public boolean reflectivelyScan() {
        return this != SERVICE_LOAD;
    }

    public boolean serviceLoad() {
        return this != ONLY_SCAN;
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
