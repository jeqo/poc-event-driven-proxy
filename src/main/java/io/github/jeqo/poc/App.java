package io.github.jeqo.poc;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import no.sysco.middleware.kafka.util.StreamsTopologyGraphviz;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proxy application entry-point.
 *
 * Workflow:
 *
 * 1. Requests via topic are processed and downstream to In Progress topic.
 * 2. Responses will come via another topic:
 *   2.1. If result already exist, then log and do nothing.
 *   2.2. If no result, clean in-progress request and downstream to Result.
 * 3. Schedule a timeout scan
 *   3.1. If timeout, then publish result.
 *   3.2. Else, do nothing.
 */
public class App {
  static final Logger LOGGER = LoggerFactory.getLogger(App.class);

  // State Store names
  static final String IN_PROGRESS_STORE_NAME = "in_progress";
  static final String RESULT_STORE_NAME = "results";

  // Topics
  static final String REQUESTS_TOPIC = "requests";
  static final String IN_PROGRESS_TOPIC = "in_progress";
  static final String RESPONSE_TOPIC = "responses";
  static final String RESULTS_TOPIC = "results";

  // Properties to define timeout
  static final Duration SCAN_FREQUENCY = Duration.ofSeconds(10);
  static final Duration TIMEOUT = Duration.ofMinutes(1);

  public static void main(String[] args) {
    var client = new Client();

    var builder = new StreamsBuilder();

    // Process incoming requests
    builder
        .stream(REQUESTS_TOPIC, Consumed.with(Serdes.String(), Serdes.String()))
        .map((key, value) -> {
          client.sendRequest(value); //RPC request
          return KeyValue.pair(key, "OK");
        })
        .to(IN_PROGRESS_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

    // Check timeouts
    builder
        .addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(IN_PROGRESS_STORE_NAME),
            Serdes.String(),
            Serdes.Long()))
        .stream(IN_PROGRESS_TOPIC, Consumed.with(Serdes.String(), Serdes.String()))
        .transform(() -> new Transformer<String, String, KeyValue<String, String>>() {
          KeyValueStore<String, Long> stateStore;

          @Override public void init(ProcessorContext context) {
            this.stateStore = (KeyValueStore<String, Long>) context.getStateStore(IN_PROGRESS_STORE_NAME);
            // Schedule timeout validation
            context.schedule(
                SCAN_FREQUENCY,
                PunctuationType.WALL_CLOCK_TIME,
                timestamp -> {
                  long timeouts = 0;
                  try (final KeyValueIterator<String, Long> all = stateStore.all()) {
                    while (all.hasNext()) {
                      final KeyValue<String, Long> keyValue = all.next();
                      // Validate timeout
                      if (Instant.now().toEpochMilli() - keyValue.value > TIMEOUT.toMillis()) {
                        context.forward(keyValue.key, "TIMEOUT");
                        timeouts ++;
                      }
                    }
                    LOGGER.warn("Timeout on {} records", timeouts);
                  }
                }
            );
          }

          @Override public KeyValue<String, String> transform(String key, String value) {
            if (value != null) {
              stateStore.put(key, Instant.now().toEpochMilli());
            }
            return null;
          }

          @Override public void close() {
          }
        }, IN_PROGRESS_STORE_NAME)
        .to(RESPONSE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

    // Process responses
    builder
        .addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(RESULT_STORE_NAME),
            Serdes.String(),
            Serdes.String()))
        .stream(RESPONSE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()))
        .transform(() -> new Transformer<String, String, KeyValue<String, String>>() {
          KeyValueStore<String, Long> inProgressStore;
          KeyValueStore<String, String> resultsStore;

          @Override public void init(ProcessorContext context) {
            this.inProgressStore = (KeyValueStore<String, Long>) context.getStateStore(IN_PROGRESS_STORE_NAME);
            this.resultsStore = (KeyValueStore<String, String>) context.getStateStore(RESULT_STORE_NAME);
          }

          @Override public KeyValue<String, String> transform(String key, String value) {
            // Validate only one response is published as result.
            var result = resultsStore.get(key);
            if (result == null) {
              inProgressStore.delete(key); // remove from timeout checks
              resultsStore.put(key, value);
              return KeyValue.pair(key, value);
            } else {
              LOGGER.warn("Response came late or repeated: {}=>{}", key, value);
              // TODO: remove if repeated to clean up space?
              return null;
            }
          }

          @Override public void close() {
          }
        }, RESULT_STORE_NAME, IN_PROGRESS_STORE_NAME)
        .to(RESULTS_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

    // Result validation. Not needed in real-life scenario.
    builder.stream(RESULTS_TOPIC, Consumed.with(Serdes.String(), Serdes.String()))
        .foreach((key, value) -> System.out.printf("Result %s=>%s%n", key, value));

    var topology = builder.build();

    // Print out topology graph
    LOGGER.info(StreamsTopologyGraphviz.print(topology));

    // Kafka Streams configuration
    var streamsConfig = new Properties();
    streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, "app");
    streamsConfig.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
    streamsConfig.put(StreamsConfig.EXACTLY_ONCE, "true");
    streamsConfig.put(StreamsConfig.STATE_DIR_CONFIG, "target/kafka-streams");

    var streams = new KafkaStreams(topology, streamsConfig);

    streams.start();

    Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
  }
}
