//! Accesso dal link nell'email: Supabase reindirizza a http://localhost:47823/#access_token=…
//! Il frammento (#…) non arriva al server, quindi la pagina servita qui lo rimanda con una
//! seconda richiesta (/done?…), che viene passata all'app come evento "login-callback".

use std::io::{BufRead, BufReader, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use tauri::{Emitter, Manager};

pub const PORT: u16 = 47823;
static RUNNING: AtomicBool = AtomicBool::new(false);

const PAGE: &str = r#"<!doctype html><html><head><meta charset="utf-8"><title>myPodder</title>
<style>body{font-family:system-ui,sans-serif;display:grid;place-items:center;height:100vh;margin:0;background:#fdf7ff;color:#1d1b20}
@media(prefers-color-scheme:dark){body{background:#141218;color:#e6e0e9}}main{text-align:center;max-width:28rem;padding:1rem}</style></head>
<body><main><h1>myPodder</h1><p id="m">…</p></main><script>
const p = location.hash.slice(1) || location.search.slice(1);
fetch('/done?' + p).then(() => {
  document.getElementById('m').textContent = p.includes('access_token')
    ? 'Accesso completato: puoi chiudere questa scheda e tornare a myPodder. / Signed in: you can close this tab.'
    : 'Accesso non riuscito: riprova da myPodder. / Sign-in failed: try again from myPodder.';
  history.replaceState(null, '', '/');
});
</script></body></html>"#;

/// Avvia (se non è già attivo) il server che attende il ritorno dal link; resta attivo 30 minuti.
#[tauri::command]
pub fn start_login_server(app: tauri::AppHandle) -> Result<String, String> {
    let url = format!("http://localhost:{PORT}");
    if RUNNING.swap(true, Ordering::SeqCst) {
        return Ok(url);
    }
    let listener = match TcpListener::bind(("127.0.0.1", PORT)) {
        Ok(l) => l,
        Err(e) => {
            RUNNING.store(false, Ordering::SeqCst);
            return Err(e.to_string());
        }
    };
    listener.set_nonblocking(true).map_err(|e| e.to_string())?;
    std::thread::spawn(move || {
        let deadline = Instant::now() + Duration::from_secs(30 * 60);
        while Instant::now() < deadline {
            match listener.accept() {
                Ok((stream, _)) => {
                    if handle(stream, &app) {
                        break;
                    }
                }
                Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                    std::thread::sleep(Duration::from_millis(150))
                }
                Err(_) => break,
            }
        }
        RUNNING.store(false, Ordering::SeqCst);
    });
    Ok(url)
}

/// Gestisce una richiesta; true quando è arrivato il risultato dell'accesso.
fn handle(mut stream: TcpStream, app: &tauri::AppHandle) -> bool {
    let _ = stream.set_nonblocking(false);
    let _ = stream.set_read_timeout(Some(Duration::from_secs(5)));
    let mut line = String::new();
    if BufReader::new(&stream).read_line(&mut line).is_err() {
        return false;
    }
    let path = line.split_whitespace().nth(1).unwrap_or("/").to_string();
    if let Some(query) = path.strip_prefix("/done?") {
        respond(&mut stream, "text/plain", "ok");
        let _ = app.emit("login-callback", query.to_string());
        if let Some(w) = app.get_webview_window("main") {
            let _ = w.unminimize();
            let _ = w.set_focus();
        }
        return query.contains("access_token");
    }
    if path == "/favicon.ico" {
        respond(&mut stream, "text/plain", "");
        return false;
    }
    respond(&mut stream, "text/html; charset=utf-8", PAGE);
    false
}

fn respond(stream: &mut TcpStream, content_type: &str, body: &str) {
    let _ = write!(
        stream,
        "HTTP/1.1 200 OK\r\nContent-Type: {content_type}\r\nContent-Length: {}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n{body}",
        body.len()
    );
}
