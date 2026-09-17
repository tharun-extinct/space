use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::collections::HashMap;
use std::fs;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::{Path, PathBuf};
use std::ptr;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Mutex, OnceLock};

const MAX_INSTANCE_ID_LENGTH: usize = 128;
const MAX_PACKAGE_DESCRIPTOR_LENGTH: usize = 512;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum InstanceState {
    Mounted,
    Running,
    Stopped,
}

impl InstanceState {
    fn label(self) -> &'static str {
        match self {
            Self::Mounted => "mounted",
            Self::Running => "running",
            Self::Stopped => "stopped",
        }
    }
}

#[derive(Debug)]
struct Instance {
    storage_path: PathBuf,
    state: InstanceState,
}

#[derive(Debug)]
struct Runtime {
    root: PathBuf,
    instances: HashMap<String, Instance>,
}

impl Runtime {
    fn create(root: &Path) -> Result<Self, String> {
        fs::create_dir_all(root).map_err(|e| format!("cannot create runtime root: {e}"))?;
        Ok(Self {
            root: fs::canonicalize(root).map_err(|e| format!("cannot resolve runtime root: {e}"))?,
            instances: HashMap::new(),
        })
    }

    fn mount(&mut self, id: &str, path: &Path) -> Result<(), String> {
        validate_instance_id(id)?;
        validate_uncreated_path(&self.root, path)?;
        fs::create_dir_all(path).map_err(|e| format!("cannot create instance storage: {e}"))?;
        let path = fs::canonicalize(path).map_err(|e| format!("cannot resolve instance storage: {e}"))?;
        if path == self.root || !path.starts_with(&self.root) {
            return Err("instance storage must be contained by the runtime root".into());
        }
        match self.instances.get(id) {
            Some(instance) if instance.state == InstanceState::Running => {
                return Err("a running instance cannot be remounted".into())
            }
            Some(instance) if instance.storage_path != path => {
                return Err("an instance cannot be remounted at a different path".into())
            }
            _ => {}
        }
        self.instances.insert(id.into(), Instance { storage_path: path, state: InstanceState::Mounted });
        Ok(())
    }

    fn start(&mut self, id: &str, package: &str) -> Result<(), String> {
        validate_instance_id(id)?;
        validate_package_descriptor(package)?;
        let instance = self.instances.get_mut(id).ok_or("instance storage must be mounted before start")?;
        match instance.state {
            InstanceState::Mounted | InstanceState::Stopped => {
                instance.state = InstanceState::Running;
                Ok(())
            }
            InstanceState::Running => Err("instance is already running".into()),
        }
    }

    fn stop(&mut self, id: &str) -> Result<(), String> {
        validate_instance_id(id)?;
        let instance = self.instances.get_mut(id).ok_or("unknown instance")?;
        if instance.state != InstanceState::Running {
            return Err("only a running instance can be stopped".into());
        }
        instance.state = InstanceState::Stopped;
        Ok(())
    }

    fn status(&self, id: &str) -> Result<&'static str, String> {
        validate_instance_id(id)?;
        Ok(self.instances.get(id).map_or("missing", |entry| entry.state.label()))
    }
}

fn validate_uncreated_path(root: &Path, path: &Path) -> Result<(), String> {
    if !path.is_absolute()
        || path
            .components()
            .any(|component| matches!(component, std::path::Component::ParentDir))
    {
        return Err("instance storage must be an absolute path without parent traversal".into());
    }

    let mut ancestor = path;
    while !ancestor.exists() {
        ancestor = ancestor
            .parent()
            .ok_or_else(|| "instance storage has no existing ancestor".to_owned())?;
    }
    let ancestor = fs::canonicalize(ancestor)
        .map_err(|e| format!("cannot resolve instance storage ancestor: {e}"))?;
    if !ancestor.starts_with(root) {
        return Err("instance storage must be contained by the runtime root".into());
    }
    Ok(())
}

fn validate_instance_id(id: &str) -> Result<(), String> {
    if id.is_empty() || id.len() > MAX_INSTANCE_ID_LENGTH
        || !id.bytes().all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b'-'))
    {
        return Err("instance ID must contain only ASCII letters, digits, '.', '_' or '-'".into());
    }
    Ok(())
}

fn validate_package_descriptor(package: &str) -> Result<(), String> {
    if package.is_empty() || package.len() > MAX_PACKAGE_DESCRIPTOR_LENGTH
        || !package.bytes().all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b'-' | b':'))
    {
        return Err("invalid package descriptor".into());
    }
    Ok(())
}

static RUNTIMES: OnceLock<Mutex<HashMap<i64, Runtime>>> = OnceLock::new();
static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);

fn registry() -> &'static Mutex<HashMap<i64, Runtime>> {
    RUNTIMES.get_or_init(|| Mutex::new(HashMap::new()))
}

fn with_registry<T>(action: impl FnOnce(&mut HashMap<i64, Runtime>) -> Result<T, String>) -> Result<T, String> {
    let mut runtimes = registry().lock().map_err(|_| "runtime registry is unavailable".to_owned())?;
    action(&mut runtimes)
}

fn java_string(env: &mut JNIEnv, value: JString) -> Result<String, String> {
    env.get_string(&value).map(Into::into).map_err(|e| format!("invalid Java string: {e}"))
}

fn throw(env: &mut JNIEnv, class: &str, message: impl AsRef<str>) {
    let _ = env.throw_new(class, message.as_ref());
}

fn guard<T>(env: &mut JNIEnv, default: T, action: impl FnOnce(&mut JNIEnv) -> Result<T, String>) -> T {
    match catch_unwind(AssertUnwindSafe(|| action(env))) {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => { throw(env, "java/lang/IllegalStateException", error); default }
        Err(_) => { throw(env, "java/lang/RuntimeException", "native runtime panicked"); default }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_parallelspace_controller_runtime_NativeRuntime_createRuntime(
    mut env: JNIEnv, _this: JObject, storage_path: JString,
) -> jlong {
    guard(&mut env, 0, |env| {
        let path = java_string(env, storage_path)?;
        let runtime = Runtime::create(Path::new(&path))?;
        let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
        with_registry(|runtimes| { runtimes.insert(handle, runtime); Ok(handle) })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_parallelspace_controller_runtime_NativeRuntime_getApiVersion(
    _env: JNIEnv,
    _class: JObject,
) -> jni::sys::jint {
    1
}

#[no_mangle]
pub extern "system" fn Java_com_parallelspace_controller_runtime_NativeRuntime_mountInstanceStorage(
    mut env: JNIEnv, _this: JObject, handle: jlong, id: JString, path: JString,
) -> jboolean {
    guard(&mut env, JNI_FALSE, |env| {
        let id = java_string(env, id)?;
        let path = java_string(env, path)?;
        with_registry(|runtimes| runtimes.get_mut(&handle).ok_or("invalid runtime handle".into())?.mount(&id, Path::new(&path)))?;
        Ok(JNI_TRUE)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_parallelspace_controller_runtime_NativeRuntime_startInstance(
    mut env: JNIEnv, _this: JObject, handle: jlong, id: JString, package: JString,
) {
    guard(&mut env, (), |env| {
        let id = java_string(env, id)?;
        let package = java_string(env, package)?;
        with_registry(|runtimes| runtimes.get_mut(&handle).ok_or("invalid runtime handle".into())?.start(&id, &package))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_parallelspace_controller_runtime_NativeRuntime_stopInstance(
    mut env: JNIEnv, _this: JObject, handle: jlong, id: JString,
) {
    guard(&mut env, (), |env| {
        let id = java_string(env, id)?;
        with_registry(|runtimes| runtimes.get_mut(&handle).ok_or("invalid runtime handle".into())?.stop(&id))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_parallelspace_controller_runtime_NativeRuntime_getRuntimeStatus(
    mut env: JNIEnv, _this: JObject, handle: jlong, id: JString,
) -> jstring {
    guard(&mut env, ptr::null_mut(), |env| {
        let id = java_string(env, id)?;
        let status = with_registry(|runtimes| Ok(runtimes.get(&handle).ok_or("invalid runtime handle")?.status(&id)?.to_owned()))?;
        env.new_string(status).map(|value| value.into_raw()).map_err(|e| format!("cannot allocate status string: {e}"))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_parallelspace_controller_runtime_NativeRuntime_destroyRuntime(
    mut env: JNIEnv, _this: JObject, handle: jlong,
) {
    guard(&mut env, (), |_env| with_registry(|runtimes| {
        runtimes.remove(&handle).map(|_| ()).ok_or("invalid runtime handle".into())
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn temp(name: &str) -> PathBuf {
        let id = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
        std::env::temp_dir().join(format!("parallel-runtime-{name}-{id}"))
    }

    #[test]
    fn validates_instance_ids() {
        assert!(validate_instance_id("profile-1_copy.example").is_ok());
        assert!(validate_instance_id("../escape").is_err());
        assert!(validate_instance_id("").is_err());
    }

    #[test]
    fn rejects_storage_outside_runtime_root() {
        let root = temp("root");
        let outside = temp("outside");
        let mut runtime = Runtime::create(&root).unwrap();
        assert!(runtime.mount("instance-1", &outside).is_err());
        assert!(!outside.exists());
        let _ = fs::remove_dir_all(root);
        let _ = fs::remove_dir_all(outside);
    }

    #[test]
    fn enforces_state_transitions() {
        let root = temp("states");
        let storage = root.join("instances/instance-1");
        let mut runtime = Runtime::create(&root).unwrap();
        assert_eq!(runtime.status("instance-1").unwrap(), "missing");
        assert!(runtime.start("instance-1", "com.example.app").is_err());
        runtime.mount("instance-1", &storage).unwrap();
        runtime.start("instance-1", "com.example.app").unwrap();
        assert!(runtime.start("instance-1", "com.example.app").is_err());
        runtime.stop("instance-1").unwrap();
        assert!(runtime.stop("instance-1").is_err());
        runtime.start("instance-1", "com.example.app").unwrap();
        let _ = fs::remove_dir_all(root);
    }
}
