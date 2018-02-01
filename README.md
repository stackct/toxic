# toxic

[![Build Status](https://travis-ci.org/plxis/toxic.svg?branch=master)](https://travis-ci.org/plxis/toxic)

Toxic (Task Orchestrator for XML-Integrated Components) is a software development tool with standalone task orchestration capabilites from the command-line, as well as optional hosted functionality.

In the standalone mode, Toxic will traverse a directory structure looking for registered task files (ending in extensions such as .xml, .sql, etc) and execute those files as tasks. At the conclusion of the traversal, the results will be output to the console and saved into a jUnit XML format. This mode is often used for providing integration, or black-box testing.

In the hosted mode, Toxic will run as a server and watching a jobs/pending directory for files ending with a .job extension. Those files are properties files that describe a job to run. The job is made up of one or more directory traversal targets. When the job completes, the results are available on the Toxic UI, similar to a CI server that provides unit test results.

Because Toxic is a generic task orchestrator, it is able to perform a variety of unrelated tasks, and can be configured to acts as a team's build server, unit test server, integration test server, environment provisioner, product deployer, etc.

## Background

Toxic originally was created to automated the repeatable tests that humans were executing against a XML protocol-based application. This is what is mentioned above as the command-line execution tool. It was built in a couple of days to quickly get results, and wasn't intended for use outside of a small team of developers, small as in less than 5. As time went on, more capabilities were added and after several years the tool has become much larger than it was ever envisioned, and now has a server-hosted mode that might resemble something more like a CI tool than a XML request/response comparing tool.

One significant drawback to Toxic's upbringing is that it was never a formal project with resource budgeting, codereview requirements, unit test coverage. So when you observe deficiencies with the product, such as the lack of built-in multi-lingual support, consider this background.

## User Guide

* [User Guide](docs/user-guide.md)

## Testing DSL

Toxic contains a DSL (domain-specific language) that enables expressive tests to be written leveraging Toxic primitive tasks.

* [Pickle - Testing DSL](docs/pickle.md)
