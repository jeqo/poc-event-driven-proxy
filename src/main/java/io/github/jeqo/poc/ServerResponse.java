package io.github.jeqo.poc;

import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class ServerResponse {
  public static void main(String[] args) throws Exception {
    // Producer configuration
    var producerConfig = new Properties();
    producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
    producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    Producer<String, String> kafkaProducer = new KafkaProducer<>(producerConfig);

    ProducerRecord<String, String> record = new ProducerRecord<>(App.RESPONSE_TOPIC, "4", "a2");
    kafkaProducer.send(record).get();
    kafkaProducer.flush();
  }
}
