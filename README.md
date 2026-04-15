
# central-reference-data-inbound-orchestrator

The Central Reference Data Inbound Orchestrator responsibilities:
- Receive and store SOAP XML messages from [public-soap-proxy](https://github.com/hmrc/aws-ami-public-soap-proxy), supporting both CS/RD2 export messages and delta subscription messages
- Keep track of the AV Scanning progress of an update
- Forward successfully scanned messages to EIS

## Development Setup
- Run mongo: `docker run --restart unless-stopped --name mongodb -p 27017:27017 -d percona/percona-server-mongodb:6.0 --replSet rs0` (please use latest version as per MDTP best practices, this is just an example)
- Run locally: `sbt run` which runs on port `7250` by default
- To run with test-only routes enabled: `sbt run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes`

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
processed we need to forward the message wrapper on to EIS(API number CRDL01 a.k.a. CSRD120 and  CSRD130). There are a few configuration
entries which help define this process.

The service forwards to two different EIS endpoints depending on the inbound message type:
- **Export messages** (CS/RD2 reference data): EIS API number CRDL01 a.k.a. CSRD120
- **Subscription messages** (EU delta): EIS subscription endpoint - CSRD130

### Endpoint definition

The actual endpoint we call is defined using the `microservice.services.eis-api` group.
The main entries are the standard `host`, `port` and `protocol`.

We include two `path` entries which define the actual endpoints on the EIS server:
- `exportMessagePath`: path for CS/RD2 export (reference data) messages
- `subscriptionMessagePath`: path for EU delta subscription messages

There are also two bearer token entries to allow us to define the security tokens required
to connect to the EIS server:
- `extractBearerToken`: token for export (extract) messages
- `subscriptionBearerToken`: token for subscription messages

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

The service recognises four SOAP action types:
- `IsAlive` — IsAlive health check for the service
- `ReferenceDataExport` — CS/RD2 reference data export message
- `ReferenceDataSubscription` — EU delta subscription message



**Export messages** are validated against the [SOAP Envelope schema](conf/schemas/soap-envelope.xsd), which has been modified to require the body to validate against a simplified version of the [ReferenceDataExportReceiverCBS](conf/schemas/request-message.xsd) callback service schema from the CSRD2 Service Specification.

**Subscription messages** are validated against a separate set of schemas located in [conf/schemas/subscription/](conf/schemas/subscription/).

Originally, the service did this as a two-step process, first validating the SOAP Envelope and then validating the message body as a standalone document. However, this would not have worked if there were namespaced elements in the message body as the namespace declarations would be on the SOAP Envelope element.

After the request has passed this validation, we extract the fields required from the request message and create a record in the message wrapper collection.

Sample messages can be found [here](it/test/helpers/InboundSoapMessage.scala).

In the event that the simplified XML schema is inaccurate and rejects legitimate messages, we have provided a feature flag that disables XSD validation.

The configuration key for this feature flag is `microservice.features.xsdValidation`, and it is provided with a default value of `true` in [application.conf](./conf/application.conf).

There is also a `microservice.features.logIncomingMessages` flag (default: `false`) that when enabled will log the full XML body of inbound messages. This should only be enabled in non-production environments for debugging purposes.

These default values can be overridden via environment-specific app-config.


## Databases
### Message wrapper Collection
When we receive a successful POST request to our root endpoint `inboundController` we create a record in the Message wrapper collection. This contains the XML payload forwarded from public-soap-proxy along with various pieces of metadata such as the UID of the file.

If a message is received with the following header: `x-files-included: true` then the message will be picked up correctly. When AV scanning is successful the message wrapper is forwarded to EIS and marked as `sent`. The current TTL is set to 7 days.

The `messageType` field stores the SOAP action as a string and will be one of:
- `CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/ReceiveReferenceData`
- `CCN2.Service.Customs.Default.CSRD.ReferenceDataSubscriptionReceiverCBS/ReceiveReferenceData`
- `CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/IsAlive`
- `CCN2.Service.Customs.Default.CSRD.ReferenceDataSubscriptionReceiverCBS/IsAlive`

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
  "messageType" : "CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/ReceiveReferenceData",
  "status": "Received",
  "uid": "c04a1612-705d-4373-8840-9d137b14b30a"
}
```
</Details>

### Scalafmt

Check all project files are formatted as expected as follows:

> `sbt scalafmtCheckAll`

Format `*.sbt` and `project/*.scala` files as follows:

> `sbt scalafmtSbt`

Format all project files as follows:

> `sbt scalafmtAll`

### Tests

Run all unit tests with command:

> `sbt test`

Run all integration tests command:

>  `sbt it/test`

Run Unit and Integration Tests command:

>  `sbt test it/test`


### All tests and checks
This is an sbt command alias specific to this project. It will run a scala format
check, run unit tests, run integration tests and produce a coverage report:
> `sbt runAllChecks`

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
