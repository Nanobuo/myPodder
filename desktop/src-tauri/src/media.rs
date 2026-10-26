//! Server locale (solo 127.0.0.1) che serve gli episodi scaricati al player, con supporto alle
//! richieste parziali (Range) necessarie per spostarsi nell'audio. Il protocollo "asset" di Tauri
//! non è riproducibile dal player di WebKit su Linux e macOS, un indirizzo http sì.
//! Ogni avvio usa una porta e un token casuali: senza token il server non risponde.

use std::fs::File;
use std::io::{BufRead, BufReader, Read, Seek, SeekFrom, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::OnceLock;

static BASE: OnceLock<String> = OnceLock::new();

fn token() -> String {
    use std::collections::hash_map::RandomState;
    use std::hash::{BuildHasher, Hasher};
    let mut out = String::new();
    for _ in 0..2 {
        let mut h = RandomState::new().build_hasher();
        h.write_u128(std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos());
        out.push_str(&format!("{:016x}", h.finish()));
    }
    out
}

/// Indirizzo base del server (es. http://127.0.0.1:43210/abcd…/); lo avvia alla prima chiamata.
#[tauri::command]
pub fn media_base_url() -> Result<String, String> {
    if let Some(b) = BASE.get() {
        return Ok(b.clone());
    }
    let listener = TcpListener::bind(("127.0.0.1", 0)).map_err(|e| e.to_string())?;
    let port = listener.local_addr().map_err(|e| e.to_string())?.port();
    let secret = token();
    let base = format!("http://127.0.0.1:{port}/{secret}/");
    let prefix = format!("/{secret}/");
    std::thread::spawn(move || {
        for stream in listener.incoming().flatten() {
            let prefix = prefix.clone();
            std::thread::spawn(move || {
                let _ = serve(stream, &prefix);
            });
        }
    });
    Ok(BASE.get_or_init(|| base).clone())
}

fn percent_decode(s: &str) -> String {
    let bytes = s.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let Ok(v) = u8::from_str_radix(&s[i + 1..i + 3], 16) {
                out.push(v);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn content_type(path: &str) -> &'static str {
    let lower = path.to_lowercase();
    if lower.ends_with(".m4a") || lower.ends_with(".mp4") || lower.ends_with(".aac") {
        "audio/mp4"
    } else if lower.ends_with(".ogg") || lower.ends_with(".opus") {
        "audio/ogg"
    } else if lower.ends_with(".wav") {
        "audio/wav"
    } else {
        "audio/mpeg"
    }
}

fn serve(stream: TcpStream, prefix: &str) -> std::io::Result<()> {
    let mut reader = BufReader::new(stream.try_clone()?);
    let mut out = stream;
    loop {
        let mut line = String::new();
        if reader.read_line(&mut line)? == 0 {
            return Ok(());
        }
        let mut parts = line.split_whitespace();
        let method = parts.next().unwrap_or("").to_string();
        let target = parts.next().unwrap_or("").to_string();
        let mut range: Option<(u64, Option<u64>)> = None;
        let mut keep_alive = true;
        loop {
            let mut h = String::new();
            if reader.read_line(&mut h)? == 0 || h == "\r\n" || h == "\n" {
                break;
            }
            let lower = h.to_lowercase();
            if let Some(v) = lower.strip_prefix("range:") {
                if let Some(spec) = v.trim().strip_prefix("bytes=") {
                    let mut it = spec.split(',').next().unwrap_or("").splitn(2, '-');
                    let start = it.next().unwrap_or("").trim().parse::<u64>().ok();
                    let end = it.next().unwrap_or("").trim().parse::<u64>().ok();
                    range = start.map(|s| (s, end));
                }
            } else if lower.starts_with("connection:") && lower.contains("close") {
                keep_alive = false;
            }
        }

        let Some(rest) = target.strip_prefix(prefix) else {
            write!(out, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n")?;
            return Ok(());
        };
        let path = percent_decode(rest.split('?').next().unwrap_or(""));
        let mut file = match File::open(&path) {
            Ok(f) => f,
            Err(_) => {
                write!(out, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n")?;
                return Ok(());
            }
        };
        let size = file.metadata()?.len();
        let (start, end, status) = match range {
            Some((s, e)) if s < size => (s, e.unwrap_or(size - 1).min(size - 1), "206 Partial Content"),
            Some(_) => {
                write!(out, "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */{size}\r\nContent-Length: 0\r\n\r\n")?;
                continue;
            }
            None => (0, size.saturating_sub(1), "200 OK"),
        };
        let len = if size == 0 { 0 } else { end - start + 1 };
        let mut headers = format!(
            "HTTP/1.1 {status}\r\nContent-Type: {}\r\nAccept-Ranges: bytes\r\nContent-Length: {len}\r\nAccess-Control-Allow-Origin: *\r\nCache-Control: no-store\r\n",
            content_type(&path)
        );
        if status.starts_with("206") {
            headers.push_str(&format!("Content-Range: bytes {start}-{end}/{size}\r\n"));
        }
        headers.push_str(if keep_alive { "Connection: keep-alive\r\n\r\n" } else { "Connection: close\r\n\r\n" });
        out.write_all(headers.as_bytes())?;
        if method != "HEAD" && len > 0 {
            file.seek(SeekFrom::Start(start))?;
            let mut remaining = len;
            const CHUNK: usize = 64 * 1024;
            let mut buf = vec![0u8; CHUNK];
            while remaining > 0 {
                let want = remaining.min(CHUNK as u64) as usize;
                let n = file.read(&mut buf[..want])?;
                if n == 0 {
                    break;
                }
                out.write_all(&buf[..n])?;
                remaining -= n as u64;
            }
        }
        out.flush()?;
        if !keep_alive {
            return Ok(());
        }
    }
}
