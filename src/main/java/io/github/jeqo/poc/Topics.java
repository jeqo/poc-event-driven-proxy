package io.github.jeqo.poc;

import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import static io.github.jeqo.poc.App.IN_PROGRESS_TOPIC;
import static io.github.jeqo.poc.App.REQUESTS_TOPIC;
import static io.github.jeqo.poc.App.RESPONSE_TOPIC;
import static io.github.jeqo.poc.App.RESULTS_TOPIC;

public class Topics {
  public static void main(String[] args) throws Exception {
    // Create topics
    var adminConfig = new Properties();
    adminConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");

    var adminClient = AdminClient.create(adminConfig);
    Collection<NewTopic> newTopics = List.of(
        new NewTopic(REQUESTS_TOPIC, 1, (short) 1),
        new NewTopic(IN_PROGRESS_TOPIC, 1, (short) 1),
        new NewTopic(RESPONSE_TOPIC, 1, (short) 1),
        new NewTopic(RESULTS_TOPIC, 1, (short) 1));
    adminClient.createTopics(newTopics).all().get();
  }
}
