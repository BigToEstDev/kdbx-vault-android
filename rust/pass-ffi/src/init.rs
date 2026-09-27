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
    // The core logs through the `log` facade; this points it at logcat. Debug level: the core's debug
    // lines are what make a failure on a device diagnosable at all, and they carry no secrets - values
    // of fields are never logged, only names and sizes.
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("pass-ffi"),
    );
}

#[cfg(not(target_os = "android"))]
fn install_logger() {
    // On the host there is no logcat. Tests that want output can install their own logger
}
