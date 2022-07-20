# Quarkus Devtools Compat

This is testing the Quarkus CLI and the Platform:
- [Releases Compatibility Test CI](https://github.com/quarkusio/quarkus-devtools-compat/actions/workflows/releases-compatibility-test.yml) is making sure that a new platform release is compatible with previous final versions of the CLI and that the new CLI release is compatible with previous versions of the platform.
- [Quarkus Snapshot Compatibility Test CI](https://github.com/quarkusio/quarkus-devtools-compat/actions/workflows/quarkus-snapshot.yml) is making sure that the current Quarkus snapshot version is compatible with previous final versions (only latest) of the CLI and that the snapshot CLI is compatible with previous final versions of the platform (only latest).

**NOTE: For each combinations, the test consist of the default project creation then its build. **

The storage is on [GitHub](https://github.com/quarkusio/quarkus-devtools-compat/tree/main/storage/cli-compat-test):
- [broken.json](https://github.com/quarkusio/quarkus-devtools-compat/blob/main/storage/cli-compat-test/broken.json) contains known broken versions (CLI or Platform) to be ignored
- [verified.json](https://github.com/quarkusio/quarkus-devtools-compat/blob/main/storage/cli-compat-test/verified.json) contains all the combinations which has been verified (the test passed). **Once a release test has passed for a combination the result is immutable.**
- [test-failed.json](https://github.com/quarkusio/quarkus-devtools-compat/blob/main/storage/cli-compat-test/test-failed.json) contains tests combination which failed and should be analysed and manually set to be ignored in broken.json.
