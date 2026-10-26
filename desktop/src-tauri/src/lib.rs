mod login;
mod media;

use std::path::PathBuf;
use tauri::Manager;
use tauri_plugin_fs::FsExt;

/// Rende leggibili dal player i file di una cartella scelta dall'utente (download).
#[tauri::command]
fn allow_dir(app: tauri::AppHandle, path: String) -> Result<(), String> {
    app.asset_protocol_scope()
        .allow_directory(PathBuf::from(&path), true)
        .map_err(|e| e.to_string())?;
    app.fs_scope().allow_directory(PathBuf::from(&path), true).map_err(|e| e.to_string())
}

#[tauri::command]
fn file_exists(path: String) -> bool {
    PathBuf::from(path).is_file()
}

#[tauri::command]
fn delete_file(path: String) -> Result<(), String> {
    match std::fs::remove_file(&path) {
        Ok(()) => Ok(()),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(e) => Err(e.to_string()),
    }
}

#[tauri::command]
fn ensure_dir(path: String) -> Result<(), String> {
    std::fs::create_dir_all(path).map_err(|e| e.to_string())
}

#[tauri::command]
fn file_size(path: String) -> u64 {
    std::fs::metadata(path).map(|m| m.len()).unwrap_or(0)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        // Una sola finestra: aprire di nuovo l'app riporta in primo piano quella esistente.
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(w) = app.get_webview_window("main") {
                let _ = w.unminimize();
                let _ = w.show();
                let _ = w.set_focus();
            }
        }))
        .plugin(tauri_plugin_window_state::Builder::default().build())
        .plugin(tauri_plugin_sql::Builder::default().build())
        .plugin(tauri_plugin_http::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_upload::init())
        .invoke_handler(tauri::generate_handler![
            allow_dir,
            file_exists,
            delete_file,
            ensure_dir,
            file_size,
            login::start_login_server,
            media::media_base_url
        ])
        .run(tauri::generate_context!())
        .expect("error while running myPodder");
}
