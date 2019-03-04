# Proof of concept: Event-driven proxy for a web-service

## Design

[Here](DESIGN.md)

## How to run

1. Start Docker Compose: `make docker-up`

2. Run `Topics` class to create topics.

3. Run `App` class to start stream-process.

4. Run `EventRequest` to simulate a request.

5. Run `ServerResponse` to simulate a response.

if response does not arrive before timeout a message is printed out.