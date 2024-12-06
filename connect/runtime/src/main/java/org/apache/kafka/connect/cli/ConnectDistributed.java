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
package org.apache.kafka.connect.cli;

import org.apache.kafka.common.utils.Time;
import org.apache.kafka.connect.connector.policy.ConnectorClientConfigOverridePolicy;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;
import org.apache.kafka.connect.runtime.Herder;
import org.apache.kafka.connect.runtime.Worker;
import org.apache.kafka.connect.runtime.WorkerConfigTransformer;
import org.apache.kafka.connect.runtime.distributed.DistributedConfig;
import org.apache.kafka.connect.runtime.distributed.DistributedHerder;
import org.apache.kafka.connect.runtime.isolation.Plugins;
import org.apache.kafka.connect.runtime.rest.RestClient;
import org.apache.kafka.connect.runtime.rest.RestServer;
import org.apache.kafka.connect.storage.ConfigBackingStore;
import org.apache.kafka.connect.storage.Converter;
import org.apache.kafka.connect.storage.KafkaConfigBackingStore;
import org.apache.kafka.connect.storage.KafkaOffsetBackingStore;
import org.apache.kafka.connect.storage.KafkaStatusBackingStore;
import org.apache.kafka.connect.storage.StatusBackingStore;
import org.apache.kafka.connect.util.ConnectUtils;
import org.apache.kafka.connect.util.SharedTopicAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.clients.CommonClientConfigs.CLIENT_ID_CONFIG;

/**
 * <p>
 * Command line utility that runs Kafka Connect in distributed mode. In this mode, the process joins a group of other
 * workers and work (connectors and tasks) is distributed among them. This is useful for running Connect as a service,
 * where connectors can be submitted to the cluster to be automatically executed in a scalable, distributed fashion.
 * This also allows you to easily scale out horizontally, elastically adding or removing capacity simply by starting or
 * stopping worker instances.
 * </p>
 */
public class ConnectDistributed extends AbstractConnectCli<DistributedConfig> {
    private static final Logger log = LoggerFactory.getLogger(ConnectDistributed.class);

    public ConnectDistributed(String... args) {
        super(args);
    }

    @Override
    protected String usage() {
        return "ConnectDistributed worker.properties";
    }

    @Override
    protected Herder createHerder(DistributedConfig config, String workerId, Plugins plugins,
                                  ConnectorClientConfigOverridePolicy connectorClientConfigOverridePolicy,
                                  RestServer restServer, RestClient restClient) {

        String kafkaClusterId = config.kafkaClusterId();
        String clientIdBase = ConnectUtils.clientIdBase(config);
        // Create the admin client to be shared by all backing stores.
        Map<String, Object> adminProps = new HashMap<>(config.originals());
        ConnectUtils.addMetricsContextProperties(adminProps, config, kafkaClusterId);
        adminProps.put(CLIENT_ID_CONFIG, clientIdBase + "shared-admin");
        SharedTopicAdmin sharedAdmin = new SharedTopicAdmin(adminProps);

        // 创建 KafkaOffsetBackingStore，用于保存每个 Connector 当前正在处理的源的偏移量
        // 保存在管理 Kafka 的 Topic：offset.storage.topic
        KafkaOffsetBackingStore offsetBackingStore = new KafkaOffsetBackingStore(sharedAdmin, () -> clientIdBase,
                plugins.newInternalConverter(true, JsonConverter.class.getName(),
                        Collections.singletonMap(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, "false")));
        offsetBackingStore.configure(config);

        // 创建 Worker 并初始化，Worker 起多线程运行多个数据同步 Task
        Worker worker = new Worker(workerId, Time.SYSTEM, plugins, config, offsetBackingStore, connectorClientConfigOverridePolicy);
        WorkerConfigTransformer configTransformer = worker.configTransformer();

        Converter internalValueConverter = worker.getInternalValueConverter();
        // 创建 StatusBackingStore，用于保存 Connector 和 Task 的状态，保存在管理 Kafka 的 Topic：status.storage.topic
        StatusBackingStore statusBackingStore = new KafkaStatusBackingStore(Time.SYSTEM, internalValueConverter, sharedAdmin, clientIdBase);
        statusBackingStore.configure(config);

        // 创建 ConfigBackingStore，用于保存 Connector 和 Task 的配置信息，保存在管理 Kafka 的 Topic：config.storage.topic
        ConfigBackingStore configBackingStore = new KafkaConfigBackingStore(
                internalValueConverter,
                config,
                configTransformer,
                sharedAdmin,
                clientIdBase);

        // 创建分布式 Kafka Connect 的 Herder（管理者）
        // Pass the shared admin to the distributed herder as an additional AutoCloseable object that should be closed when the
        // herder is stopped. This is easier than having to track and own the lifecycle ourselves.
        return new DistributedHerder(config, Time.SYSTEM, worker,
                kafkaClusterId, statusBackingStore, configBackingStore,
                restServer.advertisedUrl().toString(), restClient, connectorClientConfigOverridePolicy,
                Collections.emptyList(), sharedAdmin);
    }

    @Override
    protected DistributedConfig createConfig(Map<String, String> workerProps) {
        return new DistributedConfig(workerProps);
    }

    public static void main(String[] args) {
        ConnectDistributed connectDistributed = new ConnectDistributed(args);
        connectDistributed.run();
    }
}
