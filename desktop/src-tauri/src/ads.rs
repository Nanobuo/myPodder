//! Comandi per il riconoscimento delle pubblicità (il lavoro vero è in ads_core).

use std::path::PathBuf;

use serde::Serialize;

use crate::ads_core;

async fn blocking<T: Send + 'static>(f: impl FnOnce() -> Result<T, String> + Send + 'static) -> Result<T, String> {
    tauri::async_runtime::spawn_blocking(f).await.map_err(|e| e.to_string())?
}

/// Calcola l'impronta del file audio `audio` e la salva in `out`. Restituisce la durata in ms.
#[tauri::command]
pub async fn ad_fingerprint(audio: String, out: String) -> Result<u64, String> {
    blocking(move || {
        let fp = ads_core::fingerprint_file(&PathBuf::from(audio))?;
        ads_core::save(&PathBuf::from(out), &fp)?;
        Ok(ads_core::frames_to_ms(fp.len()))
    })
    .await
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RepeatHit {
    /// Posizione in `others` dell'impronta in cui si ripete il tratto.
    other: usize,
    start_ms: u64,
    end_ms: u64,
    other_start_ms: u64,
    other_end_ms: u64,
}

/// Tratti dell'impronta `fp` che si ripetono in una delle impronte `others`.
#[tauri::command]
pub async fn ad_repeated(fp: String, others: Vec<String>) -> Result<Vec<RepeatHit>, String> {
    blocking(move || {
        let a = ads_core::load(&PathBuf::from(fp))?;
        let mut hits = Vec::new();
        for (k, path) in others.iter().enumerate() {
            let Ok(b) = ads_core::load(&PathBuf::from(path)) else { continue };
            for (s, e, bs, be) in ads_core::repeated(&a, &b) {
                hits.push(RepeatHit {
                    other: k,
                    start_ms: ads_core::frames_to_ms(s),
                    end_ms: ads_core::frames_to_ms(e),
                    other_start_ms: ads_core::frames_to_ms(bs),
                    other_end_ms: ads_core::frames_to_ms(be),
                });
            }
        }
        Ok(hits)
    })
    .await
}

/// Tratti dell'impronta `fp` assenti nel file `audio` (un secondo download dello stesso episodio).
#[tauri::command]
pub async fn ad_inserted(fp: String, audio: String) -> Result<Vec<[u64; 2]>, String> {
    blocking(move || {
        let local = ads_core::load(&PathBuf::from(fp))?;
        let other = ads_core::fingerprint_file(&PathBuf::from(audio))?;
        Ok(ads_core::inserted(&local, &other)
            .into_iter()
            .map(|(s, e)| [ads_core::frames_to_ms(s), ads_core::frames_to_ms(e)])
            .collect())
    })
    .await
}
