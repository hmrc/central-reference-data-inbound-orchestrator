
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

| Path - internal routes prefixed by `/central-reference-data-inbound-orchestrator` | Supported Methods | Type     | Description                                                                                        |
|-----------------------------------------------------------------------------------|-------------------|----------|----------------------------------------------------------------------------------------------------|
| `/`                                                                               | POST              | Internal | Endpoint to receive xml messages and store in mongo. [See ItTestPayloads examples](it/test/helpers)|
| `/services/crdl/callback`                                                         | POST              | Internal | Endpoint to receive antivirus Scan result from SDES.                                               |
| `/test-only/message-wrappers`                                                     | DELETE            | Test     | Endpoint to delete all message wrappers in mongo.                                                  |
| `/test-only/message-wrappers/:id`                                                 | GET               | Test     | Endpoint to get message wrapper status by id from mongo.                                           |
| `/test-only/eis-work-items`                                                       | DELETE            | Test     | Endpoint to delete all eis work items.                                                             |


## Outbound Call to EIS

When we have received confirmation that the reference data file has been successfully 
processed we need to forward the message wrapper on to EIS(API number CRDL01 a.k.a. CSRD120). There are a few configuration
entries which help define this process.

### Endpoint definition

The actual endpoint we call is defined using the `microservice.services.eis-api` group.
The main entries are the standard `host`, `port` and `protocol`. 

We also include a `path` entry which defines the actual endpoint on the EIS server.

There is also an entry, `bearerToken` to allow us to define the security token required
to connect to the EIS server.

### Asynchronous Configuration Settings

The outbound call to EIS is performed asynchronously, and it also has some logic to retry
the call if something happens during the call. The configuration entries are all in the
`poller` group.

| entry                        | data type | use                                                                                                                                                                                                         |
|------------------------------|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `initial-delay`              | duration  | How long, after the service is started, until we start looking for calls to make.                                                                                                                           |
| `interval`                   | duration  | How long to wait, after checking for a message wrapper to send, before we check again.                                                                                                                      |
| `in-progress-retry-after`    | duration  | If there is a problem making a call then we should wait for period of time before we retry sending the message. This entry records the minimum amount of time we should wait before retrying.               |
| `max-retry-count`            | integer   | How many time we should attempt to retry a call.                                                                                                                                                            |
| `start-scheduler`            | boolean   | A flag to determine whether, or not, we should start the scheduler. This should only be set to false for testing purposes.                                                                                  |
| `work-item-retention-period` | duration  | The definition of a call is called a work-item and if it is successfully sent then we automatically remove it. If it fails, after all of the retries, then it says in the database for this length of time. |

### Inbound message validation

Upon receiving the inbound soap message XML from public soap proxy, we validate it against the message XML schemas (XSD) before next step message processing.

All inbound SOAP messages are validated against the [SOAP Envelope schema](conf/schemas/soap-envelope.xsd).

The SOAP envelope schema has been modified with the additional requirement that the body validates against a simplified version of the [ReferenceDataExportReceiverCBS](conf/schemas/request-message.xsd) callback service schema from the CSRD2 Service Specification.

Originally, the service did this as a two-step process, first validating the SOAP Envelope and then validating the message body as a standalone document. However, this would not have worked if there were namespaced elements in the message body as the namespace declarations would be on the SOAP Envelope element.

After the request has passed this validation, we extract the fields required from the request message and create a record in the message wrapper collection.

Sample messages can be found [here](it/test/helpers/InboundSoapMessage.scala).

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
  "status": "Received",
  "uid": "c04a1612-705d-4373-8840-9d137b14b30a"
}
```
</Details>

### All tests and checks
This is an sbt command alias specific to this project. It will run a scala format
check, run unit tests, run integration tests and produce a coverage report:
> `sbt runAllChecks`

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
