package com.afrochow.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism:}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String saslJaasConfig;

    @Value("${spring.kafka.properties.ssl.truststore.location:}")
    private String truststoreLocation;

    @Value("${spring.kafka.properties.ssl.truststore.password:}")
    private String truststorePassword;

    @Value("${spring.kafka.properties.ssl.truststore.type:PKCS12}")
    private String truststoreType;

    @Value("${spring.kafka.properties.ssl.keystore.location:}")
    private String keystoreLocation;

    @Value("${spring.kafka.properties.ssl.keystore.password:}")
    private String keystorePassword;

    @Value("${spring.kafka.properties.ssl.keystore.type:PKCS12}")
    private String keystoreType;

    @Value("${spring.kafka.properties.ssl.key.password:}")
    private String keyPassword;

    @Value("${spring.kafka.consumer.group-id:afrochow-notification-service}")
    private String consumerGroupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${app.kafka.topics.domain-events-dlq:afrochow.domain-events.dlq}")
    private String domainEventsDlqTopic;

    @Value("${app.kafka.consumers.retry-backoff-ms:1000}")
    private long consumerRetryBackoffMs;

    @Value("${app.kafka.consumers.max-retry-attempts:3}")
    private long consumerMaxRetryAttempts;

    @Bean
    @ConditionalOnMissingBean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = commonConfig();
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 10);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaTemplate<String, String> kafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = commonConfig();
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(domainEventsDlqTopic, record.partition())
        );

        FixedBackOff backOff = new FixedBackOff(consumerRetryBackoffMs, consumerMaxRetryAttempts);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    private Map<String, Object> commonConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);

        putIfPresent(config, SaslConfigs.SASL_MECHANISM, saslMechanism);
        putIfPresent(config, SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
        putIfPresent(config, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation);
        putIfPresent(config, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
        putIfPresent(config, SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, truststoreType);
        putIfPresent(config, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keystoreLocation);
        putIfPresent(config, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword);
        putIfPresent(config, SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, keystoreType);
        putIfPresent(config, SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyPassword);

        return config;
    }

    private void putIfPresent(Map<String, Object> config, String key, String value) {
        if (value != null && !value.isBlank()) {
            config.put(key, value);
        }
    }
}
