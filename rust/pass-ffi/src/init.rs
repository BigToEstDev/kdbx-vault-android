//! One time setup of the native side.
//!
//! Called from every entry point rather than from `JNI_OnLoad`: a `Once` is cheap, and it removes the
//! question of what happens if the jvm loads the library in an order we did not expect.

use std::sync::Once;

use crate::key_store;

static INIT: Once = Once::new();

pub(crate) fn ensure() {
    INIT.call_once(|| {
        install_logger();
        key_store::install();
        log::debug!("pass-ffi initialised");
    });
}

#[cfg(target_os = "android")]
fn install_logger() {
    // The core logs through the `log` facade; this points it at logcat.
    //
    // Debug while developing: the core's debug lines are what make a failure on a device diagnosable at
    // all. In a release build they are not wanted - values of fields are never logged, but the path of
    // the database file and its name are, and logcat is readable by anything with debugging access.
    //
    // This is the runtime half of the answer, and the smaller one: the ceiling is compiled in through
    // `log/release_max_level_warn` in Cargo.toml, which removes the macro calls altogether. Both are
    // here because they answer different questions - the feature decides what exists in the `.so`, this
    // decides what the logger accepts - and a release build where the two disagreed would be confusing
    // to read.
    let level = if cfg!(debug_assertions) {
        log::LevelFilter::Debug
    } else {
        log::LevelFilter::Warn
    };

    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(level)
            .with_tag("pass-ffi"),
    );
}

#[cfg(not(target_os = "android"))]
fn install_logger() {
    // On the host there is no logcat. Tests that want output can install their own logger
}
