
# central-reference-data-inbound-orchestrator

The Central Reference Data Inbound Orchestrator responsibilities:
- Store the CS/RD2 XML details from [public-soap-proxy](https://github.com/hmrc/aws-ami-public-soap-proxy)
- Keep track of the AV Scanning progress of an update

## Development Setup
- Run mongo: `docker run -d -p 27017:27017 mongo:4.2.18` (please use latest version as per MDTP best practices, this is just an example)
- Run locally: `sbt run` which runs on port `7250` by default

## Tests
- Run Unit Tests: `sbt test`
- Run Integration Tests: `sbt it/test`
- Run Unit and Integration Tests: `sbt test it/test`
- Run Unit and Integration Tests with Code Coverage: `./run_tests_with_coverage.sh`

Run Acceptance Tests: see [here](https://github.com/hmrc/central-reference-data-acceptance-tests)

Run Performance Tests see [here](https://github.com/hmrc/central-reference-data-performance-tests)

## API

| Path - internal routes prefixed by `/central-reference-data-inbound-orchestrator` | Supported Methods | Type     | Description                                                                  |
|-----------------------------------------------------------------------------------|-------------------|----------|------------------------------------------------------------------------------|
| `/`                                                                               | POST              | Internal | Endpoint to receive xml messages and store in mongo. [See ItTestPayloads examples](it/helpers) |
| `/services/crdl/callback`                                                         | POST              | Internal | Endpoint to receive antivirus Scan result from SDES.                         |
| `/test-only/message-wrappers`                                                     | DELETE            | Test     | Endpoint to delete all message wrappers in mongo.                            |
| `/test-only/message-wrappers/:id`                                                 | GET               | Test     | Endpoint to get message wrapper status by id from mongo.                     |
| `/test-only/eis-work-items`                                                       | DELETE            | Test     | Endpoint to delete all eis work items.                                       |


### Inbound message validation

Upon receiving the inbound soap message XML from public soap proxy, we validate it against the message XML schemas (XSD) before next step message processing.
- All inbound soap messages are first validated against general schema [xml.xsd](conf/schemas/xml.xsd) and [soap envelope schema](conf/schemas/soap-envelope.xsd).
- Valid soap messages are then validated against the [message wrapper schema](conf/schemas/receive-reference-data-submission-result.xsd) before we create a record in the Message wrapper collection.

[Unit tests](test/uk/gov/hmrc/centralreferencedatainboundorchestrator/controllers/InboundControllerSpec.scala) and
[integration tests](it/test/uk/gov/hmrc/centralreferencedatainboundorchestrator/controllers/InboundControllerISpec.scala) for schema validation in inbound controller.
Sample valid and invalid soap messages could be found [here](it/test/helpers/InboundSoapMessage.scala).

## Databases
### Message wrapper Collection
When we receive a successful POST request to our root endpoint `inboundController` we create a record in the Message wrapper collection. This contains the XML payload forwarded from public-soap-proxy along with various pieces of metadata such as the UID of the file.

If a message is received with the following header: `x-files-included: true` then the message will be picked up correctly. When AV scanning is successful the message wrapper is forwarded to EIS and marked as `sent`. The current TTL is set to 7 days.

<Details>
<Summary>Message wrapper model</Summary>

```
{
  "_id": {
    "$oid": "66b498dc895f3155fc1b2b83"
  },
  "payload": "<MainMessage>
      <Body>
        <TaskIdentifier>780912</TaskIdentifier>
        <AttributeName>ReferenceData</AttributeName>
      	<MessageType>gZip</MessageType>
      	<IncludedBinaryObject>c04a1612-705d-4373-8840-9d137b14b30a</IncludedBinaryObject>
      	<MessageSender>CS/RD2</MessageSender>
      </Body>
    </MainMessage>",
  "lastUpdated": {
    "$date": "2024-08-08T10:07:24.435Z"
  },
  "receivedTimestamp": {
    "$date": "2024-08-08T10:07:24.435Z"
  },
  "status": "received",
  "uid": "c04a1612-705d-4373-8840-9d137b14b30a"
}
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").