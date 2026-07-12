use tauri::menu::{MenuBuilder, MenuItemBuilder};
use tauri::tray::TrayIconBuilder;
use tauri::{AppHandle, Emitter, Manager, WindowEvent};

fn show_main_window(app: &AppHandle) -> Result<(), String> {
    let window = app
        .get_webview_window("main")
        .ok_or_else(|| "Main window not found".to_string())?;

    if let Ok(true) = window.is_minimized() {
        let _ = window.unminimize();
    }

    window.show().map_err(|error| error.to_string())?;
    window.set_focus().map_err(|error| error.to_string())?;
    Ok(())
}

fn show_floating(app: &AppHandle) -> Result<(), String> {
    let window = app
        .get_webview_window("floating")
        .ok_or_else(|| "Floating window not found".to_string())?;

    window.show().map_err(|error| error.to_string())?;
    window.set_focus().map_err(|error| error.to_string())?;
    app.emit("copyboard://expand-floating", ())
        .map_err(|error| error.to_string())?;
    Ok(())
}

fn toggle_floating(app: &AppHandle) -> Result<(), String> {
    let window = app
        .get_webview_window("floating")
        .ok_or_else(|| "Floating window not found".to_string())?;

    if window.is_visible().map_err(|error| error.to_string())? {
        window.hide().map_err(|error| error.to_string())?;
    } else {
        show_floating(app)?;
    }

    Ok(())
}

#[tauri::command]
fn show_floating_window(app: AppHandle) -> Result<(), String> {
    show_floating(&app)
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_store::Builder::default().build())
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .invoke_handler(tauri::generate_handler![show_floating_window])
        .setup(|app| {
            let open_item = MenuItemBuilder::with_id("open", "Open Copyboard").build(app)?;
            let floating_item = MenuItemBuilder::with_id("floating", "Show/hide floating mode").build(app)?;
            let sync_item = MenuItemBuilder::with_id("sync", "Sync").build(app)?;
            let exit_item = MenuItemBuilder::with_id("exit", "Exit").build(app)?;

            let menu = MenuBuilder::new(app)
                .items(&[&open_item, &floating_item, &sync_item, &exit_item])
                .build()?;

            let handle = app.handle().clone();
            TrayIconBuilder::new()
                .icon(
                    app.default_window_icon()
                        .ok_or_else(|| tauri::Error::AssetNotFound("default icon".into()))?
                        .clone(),
                )
                .menu(&menu)
                .show_menu_on_left_click(true)
                .on_menu_event(move |_, event| match event.id.as_ref() {
                    "open" => {
                        let _ = show_main_window(&handle);
                    }
                    "floating" => {
                        let _ = toggle_floating(&handle);
                    }
                    "sync" => {
                        let _ = handle.emit("copyboard://sync-request", ());
                    }
                    "exit" => {
                        handle.exit(0);
                    }
                    _ => {}
                })
                .build(app)?;

            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}