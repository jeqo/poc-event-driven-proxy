# Design notes

## Goal

Achieve consistent communication with web services (synchronous or
asynchronous) by events interchange.

## Objectives

- Every request should produce a response.
- Responses should be always propagated to the outside via events.

## Assumptions

- The event distribution channel will be Kafka.
- Kafka is assumed to be highly available.
- Web Services are HTTP endpoints
- Requests will come as event messages in a Kafka topic.
- Responses, if synchronous, will be returned in the same session.
- Responses, if async, will be polled from a feed.

## Notes

### 1. Successful requests

What sucessful means is up to the service to define: if a 50X response is
a failure depends on the context. As well, a 404 might be an error or not.

This should be defined by service and SLA.

### 2. Semantics on requests

In sending a request to a web service and commiting the requests different
options can be developed.
We have 2 options depending on the load of incoming requests and expected
throughput:

- If a load is small (e.g., 10 req/s) we can consider one by one processing
    commit.
    - Thiis is done either by polling 1 by 1 records from the topic (less code,
        less performant by more calls)
    - Polling a batch and commit every record (more code, more performant by
        less calls)
- If load is higher we can commit less ofter, either by using auto-commit or by
    commiting by batch. This will mean potentially duplicated requests by
    reprocessing a batch.

### 3. Semantics on synchronous responses

When using synchronous web services, Kafka Transaction API has to be used to
ensure that a commit from request is part of the same transaction as reponse
produced message.

Other available option is to use Kafka Streams with Exactly-once enabled.

### 4. Semantics on asynchronous responses [OPEN]

When asynchronous happen on async scenarios, then it depends how responses are
avaiable, e.g., as a feed. Assuming feed is available, then it dependes on how
the feed is handled.

Messaging technologies might become more popular as a way to share data between
different parties: e.g., https://github.com/cloudevents/spec

Using a feed might not be scalable or be the right abstraction neither:
https://stackoverflow.com/questions/3952882/how-rss-and-atom-inform-client-about-updates-long-polling-or-polling-or-somethi

if they offer a way to query by date, some internal mechanism will be required
to stored commited elements.

### 5. Handling in-progress async request/response

In order to support Async Request/Response incoming requests should produce a stream of events of 
in-progress requests.

This in-progress request stream has to be used to validate timeouts on responses.

```
( requests )--->[ proxy ]--->[ service ]

[ service ]--->[ proxy ]--->( in-progress requests )

( in-progress request )--->[ service ]--->( in-progress-state )

// if response received, record it
[ service ]--->[ proxy ]--->( response )

// if local state report timeout
( in-progress-state )--->[ proxy ]--->( response )

// if no results, publish result, remove in-progress
( response )--->[ proxy ]-----+->( result )
            ( result-state )  +->( in-progress-state )

// if results, publish result, remove in-progress, and log
( response )--->[ proxy ]-----+->( log )
            ( result-state )
```