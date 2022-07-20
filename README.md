# Quarkus Devtools Compat

This is testing the Quarkus CLI and the Platform:
- [https://github.com/quarkusio/quarkus-devtools-compat/actions/workflows/releases-compatibility-test.yml](Releases Compatibility Test CI) is making sure that a new platform release is compatible with previous final versions of the CLI and that the new CLI release is compatible with previous versions of the platform.
- [https://github.com/quarkusio/quarkus-devtools-compat/actions/workflows/quarkus-snapshot.yml](Quarkus Snapshot Compatibility Test CI) is making sure that the current Quarkus snapshot version is compatible with previous final versions (only latest) of the CLI and that the snapshot CLI is compatible with previous final versions of the platform (only latest).

NOTE: We are currently only testing the default project creation then a we build it.

The storage is on [https://github.com/quarkusio/quarkus-devtools-compat/tree/main/storage/cli-compat-test](GitHub):
- [https://github.com/quarkusio/quarkus-devtools-compat/blob/main/storage/cli-compat-test/broken.json](broken.json) contains known broken versions (CLI or Platform) to be ignored
- [https://github.com/quarkusio/quarkus-devtools-compat/blob/main/storage/cli-compat-test/verified.json](verified.json) contains all the combinations which has been verified (the test passed). **Once a release test has passed for a combination the result is immutable.**
- [https://github.com/quarkusio/quarkus-devtools-compat/blob/main/storage/cli-compat-test/test-failed.json](test-failed.json) contains tests combination which failed and should be analysed and manually set to be ignored in broken.json.