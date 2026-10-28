//! This process's open handles counted by their kernel object type (File,
//! Event, Key and so on), for tests that look for handles left open.

use std::collections::BTreeMap;
use std::ffi::c_void;

use windows::Win32::System::Threading::GetCurrentProcessId;

/// `SystemExtendedHandleInformation`: every handle in the system.
const SYSTEM_EXTENDED_HANDLE_INFORMATION: u32 = 64;
/// `ObjectTypeInformation`: the type of the object a handle opens.
const OBJECT_TYPE_INFORMATION: u32 = 2;
const STATUS_INFO_LENGTH_MISMATCH: i32 = 0xC000_0004_u32 as i32;

#[repr(C)]
struct HandleEntry {
    _object: *mut c_void,
    process: usize,
    handle: usize,
    _access: u32,
    _back_trace: u16,
    type_index: u16,
    _attributes: u32,
    _reserved: u32,
}

#[repr(C)]
struct UnicodeString {
    length: u16,
    _capacity: u16,
    buffer: *const u16,
}

#[link(name = "ntdll")]
unsafe extern "system" {
    fn NtQuerySystemInformation(class: u32, info: *mut c_void, length: u32, returned: *mut u32) -> i32;
    fn NtQueryObject(
        handle: *mut c_void,
        class: u32,
        info: *mut c_void,
        length: u32,
        returned: *mut u32,
    ) -> i32;
}

/// The handle value and type index of every handle this process holds.
fn own_handles() -> Vec<(usize, u16)> {
    // SAFETY: the buffer is sized by the call's own answer and read only
    // within the count it reports.
    unsafe {
        let mut buffer = vec![0u64; 1 << 17];
        loop {
            let bytes = (buffer.len() * 8) as u32;
            let mut returned = 0u32;
            let status = NtQuerySystemInformation(
                SYSTEM_EXTENDED_HANDLE_INFORMATION,
                buffer.as_mut_ptr().cast(),
                bytes,
                &mut returned,
            );
            if status == STATUS_INFO_LENGTH_MISMATCH {
                buffer = vec![0u64; (returned as usize / 8 + 1).max(buffer.len()) * 2];
                continue;
            }
            assert!(status >= 0, "NtQuerySystemInformation failed: {status:#x}");
            break;
        }
        let count = buffer[0] as usize;
        let entries = buffer.as_ptr().add(2).cast::<HandleEntry>();
        let me = GetCurrentProcessId() as usize;
        (0..count)
            .map(|i| &*entries.add(i))
            .filter(|entry| entry.process == me)
            .map(|entry| (entry.handle, entry.type_index))
            .collect()
    }
}

/// The type name of a handle this process holds.
fn type_name(handle: usize) -> Option<String> {
    // SAFETY: the buffer is large enough for any type name, and the name
    // read lies inside it.
    unsafe {
        let mut buffer = vec![0u64; 512];
        let mut returned = 0u32;
        let status = NtQueryObject(
            handle as *mut c_void,
            OBJECT_TYPE_INFORMATION,
            buffer.as_mut_ptr().cast(),
            (buffer.len() * 8) as u32,
            &mut returned,
        );
        if status < 0 {
            return None;
        }
        let name = &*buffer.as_ptr().cast::<UnicodeString>();
        let units = std::slice::from_raw_parts(name.buffer, name.length as usize / 2);
        Some(String::from_utf16_lossy(units))
    }
}

/// How many handles of each type this process holds now.
pub(crate) fn handle_types() -> BTreeMap<String, usize> {
    let mut names: BTreeMap<u16, String> = BTreeMap::new();
    let mut counts = BTreeMap::new();
    for (handle, index) in own_handles() {
        let name = match names.get(&index) {
            Some(name) => name.clone(),
            None => {
                let name = type_name(handle).unwrap_or_else(|| format!("type {index}"));
                names.insert(index, name.clone());
                name
            }
        };
        *counts.entry(name).or_insert(0) += 1;
    }
    counts
}

/// The types whose counts differ between two readings, with the change.
pub(crate) fn changed(
    before: &BTreeMap<String, usize>,
    after: &BTreeMap<String, usize>,
) -> Vec<(String, i64)> {
    let mut kinds: Vec<&String> = before.keys().chain(after.keys()).collect();
    kinds.sort();
    kinds.dedup();
    kinds
        .into_iter()
        .filter_map(|kind| {
            let change = *after.get(kind).unwrap_or(&0) as i64 - *before.get(kind).unwrap_or(&0) as i64;
            (change != 0).then(|| (kind.clone(), change))
        })
        .collect()
}
