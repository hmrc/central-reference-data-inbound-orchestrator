
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

Run Acceptance Tests: see [here](https://github.com/hmrc/central-reference-data-acceptance-tests)

Run Performance Tests see [here](https://github.com/hmrc/central-reference-data-performance-tests)

## API

| Path - internal routes prefixed by `/central-reference-data-inbound-orchestrator` | Supported Methods | Type     | Description                                                                                 |
|-----------------------------------------------------------------------------------|-------------------|----------|---------------------------------------------------------------------------------------------|
| `/`                                                                               | POST              | Internal | Endpoint to receive xml messages. [See ItTestPayloads examples](it/helpers)              |

### Inbound message validation

To be confirmed when we know the actual messages passed to us.

## Databases
### Metadata Collection

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").