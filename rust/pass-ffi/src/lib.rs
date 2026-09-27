//! The jni bridge between Kotlin and the kdbx core.
//!
//! This crate exists because the core is `#![forbid(unsafe_code)]` and a `#[no_mangle]` function trips
//! that lint on its own, so the jni boundary cannot live there. Everything unsafe in the project is
//! therefore in this file and nowhere else; the core stays a plain, platform independent library.
//!
//! What the boundary does and deliberately does not do:
//!
//! - it converts strings and catches panics, nothing else. The decisions live in `dispatch`;
//! - it is synchronous. Argon2 and file io simply block the calling thread, and Kotlin calls from
//!   `Dispatchers.IO`. No async across the boundary and no callbacks back into Kotlin - a ticking
//!   coroutine asking for the current totp codes is simpler than a callback from Rust;
//! - a panic becomes a Java exception rather than a dead process. Ordinary failures are not exceptions:
//!   they come back inside the json envelope, see `errors`.
//!
//! The function names carry the Kotlin package (`ru.kino.dev.ffi.PassFfi`); renaming that
//! package means renaming these functions. The package must not contain underscores, because jni
//! encodes `_` in a name as `_1`.

use std::panic;

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jstring};
use jni::JNIEnv;
use zeroize::Zeroize;

mod commands;
#[cfg(test)]
mod contract;
mod dispatch;
mod errors;
mod frame;
mod init;
mod key_store;
#[cfg(test)]
mod lifecycle_tests;

/// Version of this crate plus the time it was built, so a stale `.so` is visible in the log instead of
/// being debugged for an hour. Logged by the app on start.
#[no_mangle]
pub extern "system" fn Java_ru_kino_dev_ffi_PassFfi_buildInfo<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    guarded(&mut env, |_env| {
        // The core has no version constant of its own; its revision is pinned by the checkout, and the
        // build timestamp is what tells a stale .so apart from a fresh one
        Ok(format!(
            "pass-ffi {} built at {}",
            env!("CARGO_PKG_VERSION"),
            env!("BUILD_TS"),
        ))
    })
}

/// Runs one command of the core. `argsJson` is a json object of arguments, the answer is the envelope
/// described in `errors`.
#[no_mangle]
pub extern "system" fn Java_ru_kino_dev_ffi_PassFfi_invoke<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    command: JString<'local>,
    args_json: JString<'local>,
) -> jstring {
    guarded(&mut env, |env| {
        let command = read_string(env, &command, "command")?;
        let args_json = read_string(env, &args_json, "argsJson")?;
        Ok(dispatch::run(&command, &args_json))
    })
}

/// Runs one command across the binary boundary: arguments arrive as bytes so Kotlin can wipe them after
/// the call, and a database may travel in either direction without being base64'd into json.
///
/// The answer is one frame - see `frame` for its shape - because a single return value keeps one channel
/// for failures: a refusal is still the envelope, with no payload behind it.
#[no_mangle]
pub extern "system" fn Java_ru_kino_dev_ffi_PassFfi_invokeBinary<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    command: JString<'local>,
    args: JByteArray<'local>,
    input: JByteArray<'local>,
) -> jbyteArray {
    init::ensure();

    let outcome = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        let command = read_string(&mut env, &command, "command")?;
        let mut args_bytes = read_bytes(&mut env, &args, "args")?.unwrap_or_default();
        let input_bytes = read_bytes(&mut env, &input, "input")?;

        let args_json = match std::str::from_utf8(&args_bytes) {
            Ok(text) => text.to_string(),
            Err(e) => {
                wipe(&mut args_bytes);
                return Err(format!("the arguments were not utf-8: {}", e));
            }
        };

        let answer = dispatch::run_with_bytes(&command, &args_json, input_bytes);

        // The arguments may have carried a password, so neither copy outlives the call: this one here,
        // and the Java array on the Kotlin side
        wipe(&mut args_bytes);

        Ok(frame::build(&answer.envelope, answer.payload.as_deref()))
    }));

    let frame = match outcome {
        Ok(Ok(frame)) => frame,
        Ok(Err(message)) => return throw_bytes(&mut env, &message),
        Err(payload) => {
            return throw_bytes(
                &mut env,
                &format!("pass-ffi panicked: {}", panic_message(&payload)),
            )
        }
    };

    match env.byte_array_from_slice(&frame) {
        Ok(array) => array.into_raw(),
        Err(e) => throw_bytes(
            &mut env,
            &format!("the answer could not be returned: {}", e),
        ),
    }
}

// Shared shape of every entry point: initialise once, run the body with panics caught, hand the result
// back as a Java string.
//
// A panic here is a bug in our own code, so it is thrown as a RuntimeException rather than dressed up as
// a result - it must be loud in development and it must not take the process down in production. Same
// for a jni failure while reading an argument: there is nothing sensible to return.
fn guarded<'local, F>(env: &mut JNIEnv<'local>, body: F) -> jstring
where
    F: FnOnce(&mut JNIEnv<'local>) -> Result<String, String>,
{
    init::ensure();

    let outcome = panic::catch_unwind(panic::AssertUnwindSafe(|| body(env)));

    let answer = match outcome {
        Ok(Ok(answer)) => answer,
        Ok(Err(message)) => return throw(env, &message),
        Err(payload) => {
            return throw(
                env,
                &format!("pass-ffi panicked: {}", panic_message(&payload)),
            )
        }
    };

    match env.new_string(&answer) {
        Ok(java_string) => java_string.into_raw(),
        // The answer itself could not be turned into a Java string - report it as a failure of the call
        Err(e) => throw(env, &format!("the answer could not be returned: {}", e)),
    }
}

fn read_string<'local>(
    env: &mut JNIEnv<'local>,
    value: &JString<'local>,
    name: &str,
) -> Result<String, String> {
    env.get_string(value)
        .map(|s| s.into())
        .map_err(|e| format!("the argument '{}' could not be read: {}", name, e))
}

// Returning a null jstring after throwing is the jni convention: the jvm looks at the pending exception
// and never at the value.
fn throw(env: &mut JNIEnv<'_>, message: &str) -> jstring {
    log::error!("{}", message);
    let _ = env.throw_new("java/lang/RuntimeException", message);
    std::ptr::null_mut()
}

// A null array is how Kotlin says "no bytes"; an empty array is bytes of length zero, and the two mean
// different things to a command - hence Option rather than Vec.
fn read_bytes<'local>(
    env: &mut JNIEnv<'local>,
    value: &JByteArray<'local>,
    name: &str,
) -> Result<Option<Vec<u8>>, String> {
    if value.is_null() {
        return Ok(None);
    }

    env.convert_byte_array(value)
        .map(Some)
        .map_err(|e| format!("the argument '{}' could not be read: {}", name, e))
}

// Overwrites a buffer that may have held a secret. `zeroize` rather than a plain loop: the compiler is
// free to drop writes to memory nothing reads again, and this crate is where that would matter. The core
// wipes its own buffers the same way (`db/kdbx_file.rs`).
fn wipe(buffer: &mut [u8]) {
    buffer.zeroize();
}

fn throw_bytes(env: &mut JNIEnv<'_>, message: &str) -> jbyteArray {
    log::error!("{}", message);
    let _ = env.throw_new("java/lang/RuntimeException", message);
    std::ptr::null_mut()
}

fn panic_message(payload: &Box<dyn std::any::Any + Send>) -> String {
    if let Some(s) = payload.downcast_ref::<&str>() {
        return (*s).to_string();
    }
    if let Some(s) = payload.downcast_ref::<String>() {
        return s.clone();
    }
    "a panic with no message".to_string()
}
