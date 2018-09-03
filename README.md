Deterministic CorDapp Example
=============================

This CorDapp requires Corda v4.x or above. It contains 3 modules:

- `contract`: The `Contract` and `State` classes that Corda will use inside the enclave. These
    are compiled against the deterministic subset of the Corda and Java APIs.
- `flow`: The `FlowLogic` classes that Corda will execute outside of the enclave.
- `web`: An optional module that provides a RESTful API. This is solely for demonstrating the
    flows.

# Configuring IntelliJ

Check out the project, and then execute this command:
```bash
$ gradlew installJdk
```

This will download and install the deterministic JDK artifacts into the `./jdk` directory.

Run IntelliJ, and open the `File/Project Structure/SDKs` dialogue. Click `+` to add a new JDK
SDK, selecting the project's new `./jdk` directory as this JDK's home directory. Assign this
new SDK a unique name, e.g. `1.8 (CorDapp)`.

Edit the project's top-level `build.gradle` file and assign this new SDK's name to the
`deterministic_idea_sdk` property, e.g.

```gradle
buildscript {
    ext {
        ...
        deterministic_idea_sdk = '1.8 (CorDapp)'
        ...
    }
}
```

Now open the CorDapp project within IntelliJ. Ensure that IntelliJ delegates all of this
project's build actions to Gradle, that the Project JDK is a full JDK installation (_not_ the
deterministic one!) and that Gradle uses the Project JDK.

When IntelliJ imports this Gradle project, it should now be using `1.8 (Cordapp)` as the Module
SDK for both the `contract_main` and `contract_test` modules. This will allow IntelliJ to build
the project correctly while using the deterministic Java subset for the IDE's presentation
compiler.
