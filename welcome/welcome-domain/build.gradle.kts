plugins {
    id("java-lib")
}

dependencies {
    // CredentialLimits for the password field, and the vocabulary of the core under the interfaces
    api(projects.core.coreDomain)
}
