use std::time::{SystemTime, UNIX_EPOCH};

// Stamps the build time into the library so buildInfo() can report it. Without this it is easy to
// spend an hour debugging a stale .so that Gradle never replaced.
fn main() {
    let seconds = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or_default();

    println!("cargo:rustc-env=BUILD_TS={}", seconds);
    // Only the timestamp changes between builds, so nothing else needs to trigger a rerun
    println!("cargo:rerun-if-changed=build.rs");
}
