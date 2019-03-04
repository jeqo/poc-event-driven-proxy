package io.github.jeqo.poc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Client {
  static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

  void sendRequest(String value) {
    LOGGER.info("Service called with value: {}", value);
  }
}
