//! Riconoscimento delle pubblicità dall'audio, senza dipendenze da Tauri (così si prova da solo).
//!
//! L'audio diventa un'"impronta": ogni 32 ms un numero a 32 bit che descrive come cambia
//! l'energia in 33 bande di frequenza (metodo di Haitsma e Kalker). Due registrazioni dello
//! stesso suono hanno impronte quasi uguali anche se ricodificate o a volume diverso.
//! Confrontando le impronte si trovano:
//! - i tratti che si ripetono in più episodi dello stesso podcast (spot, sigle, messaggi);
//! - i tratti che cambiano tra due download dello stesso episodio (pubblicità inserite al download).
//!
//! Il programma Android usa lo stesso metodo (ui/ads in Kotlin): i parametri vanno tenuti uguali.

use std::collections::HashMap;
use std::fs::File;
use std::io::{Read, Write};
use std::path::Path;
use std::sync::Arc;

use rustfft::num_complex::Complex32;
use rustfft::{Fft, FftPlanner};
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::DecoderOptions;
use symphonia::core::errors::Error as SymError;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

pub const RATE: u32 = 8000;
pub const FRAME: usize = 2048;
pub const HOP: usize = 256;
/// Durata di un passo dell'impronta in millisecondi.
pub const HOP_MS: f64 = HOP as f64 * 1000.0 / RATE as f64;
const BANDS: usize = 33;
const LOW_HZ: f32 = 300.0;
const HIGH_HZ: f32 = 3000.0;
const MAGIC: &[u8; 4] = b"MPF1";

pub fn ms_to_frames(ms: f64) -> usize {
    (ms / HOP_MS).round() as usize
}

pub fn frames_to_ms(frames: usize) -> u64 {
    (frames as f64 * HOP_MS).round() as u64
}

/// Calcola l'impronta da campioni mono a [RATE] Hz che arrivano un po' alla volta.
pub struct Fingerprinter {
    fft: Arc<dyn Fft<f32>>,
    window: Vec<f32>,
    edges: Vec<usize>,
    buf: Vec<f32>,
    scratch: Vec<Complex32>,
    prev: Option<[f32; BANDS]>,
    pub out: Vec<u32>,
}

impl Default for Fingerprinter {
    fn default() -> Self {
        Self::new()
    }
}

impl Fingerprinter {
    pub fn new() -> Self {
        let fft = FftPlanner::new().plan_fft_forward(FRAME);
        let window = (0..FRAME)
            .map(|i| 0.5 - 0.5 * (2.0 * std::f32::consts::PI * i as f32 / FRAME as f32).cos())
            .collect();
        let edges = band_edges();
        Self { fft, window, edges, buf: Vec::with_capacity(FRAME * 2), scratch: vec![Complex32::new(0.0, 0.0); FRAME], prev: None, out: Vec::new() }
    }

    pub fn push(&mut self, samples: &[f32]) {
        self.buf.extend_from_slice(samples);
        let mut start = 0;
        while self.buf.len() - start >= FRAME {
            self.frame(start);
            start += HOP;
        }
        self.buf.drain(..start);
    }

    fn frame(&mut self, start: usize) {
        for i in 0..FRAME {
            self.scratch[i] = Complex32::new(self.buf[start + i] * self.window[i], 0.0);
        }
        self.fft.process(&mut self.scratch);
        let mut energy = [0f32; BANDS];
        for (b, e) in energy.iter_mut().enumerate() {
            *e = self.scratch[self.edges[b]..self.edges[b + 1]].iter().map(|c| c.norm_sqr()).sum();
        }
        if let Some(prev) = self.prev {
            let mut bits = 0u32;
            for m in 0..BANDS - 1 {
                let d = (energy[m] - energy[m + 1]) - (prev[m] - prev[m + 1]);
                if d > 0.0 {
                    bits |= 1 << m;
                }
            }
            self.out.push(bits);
        }
        self.prev = Some(energy);
    }
}

fn band_edges() -> Vec<usize> {
    (0..=BANDS)
        .map(|k| {
            let f = LOW_HZ * (HIGH_HZ / LOW_HZ).powf(k as f32 / BANDS as f32);
            ((f * FRAME as f32 / RATE as f32).round() as usize).max(1)
        })
        .collect()
}

/// Porta l'audio a mono e a [RATE] Hz facendo la media dei campioni che cadono in ogni intervallo.
pub struct Downsampler {
    ratio: f64,
    pos: f64,
    acc: f32,
    n: u32,
}

impl Downsampler {
    pub fn new(in_rate: u32) -> Self {
        Self { ratio: in_rate as f64 / RATE as f64, pos: 0.0, acc: 0.0, n: 0 }
    }

    pub fn push(&mut self, interleaved: &[f32], channels: usize, out: &mut Vec<f32>) {
        let channels = channels.max(1);
        for frame in interleaved.chunks_exact(channels) {
            let mono = frame.iter().sum::<f32>() / channels as f32;
            self.acc += mono;
            self.n += 1;
            self.pos += 1.0;
            if self.pos >= self.ratio {
                self.pos -= self.ratio;
                out.push(self.acc / self.n as f32);
                self.acc = 0.0;
                self.n = 0;
            }
        }
    }
}

/// Decodifica un file audio e ne calcola l'impronta.
pub fn fingerprint_file(path: &Path) -> Result<Vec<u32>, String> {
    let file = File::open(path).map_err(|e| e.to_string())?;
    let mss = MediaSourceStream::new(Box::new(file), Default::default());
    let mut hint = Hint::new();
    if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
        hint.with_extension(ext);
    }
    let probed = symphonia::default::get_probe()
        .format(&hint, mss, &FormatOptions::default(), &MetadataOptions::default())
        .map_err(|e| format!("formato non riconosciuto: {e}"))?;
    let mut format = probed.format;
    let track = format
        .default_track()
        .ok_or("nessuna traccia audio")?
        .clone();
    let mut decoder = symphonia::default::get_codecs()
        .make(&track.codec_params, &DecoderOptions::default())
        .map_err(|e| format!("codec non supportato: {e}"))?;

    let mut fp = Fingerprinter::new();
    let mut down: Option<Downsampler> = None;
    let mut sample_buf: Option<SampleBuffer<f32>> = None;
    let mut mono = Vec::new();
    loop {
        let packet = match format.next_packet() {
            Ok(p) => p,
            Err(SymError::IoError(e)) if e.kind() == std::io::ErrorKind::UnexpectedEof => break,
            Err(SymError::ResetRequired) => break,
            Err(e) => return Err(e.to_string()),
        };
        if packet.track_id() != track.id {
            continue;
        }
        let decoded = match decoder.decode(&packet) {
            Ok(d) => d,
            // Un pacchetto rovinato non deve fermare tutto.
            Err(SymError::DecodeError(_)) => continue,
            Err(e) => return Err(e.to_string()),
        };
        let spec = *decoded.spec();
        let buf = sample_buf.get_or_insert_with(|| SampleBuffer::new(decoded.capacity() as u64, spec));
        if buf.capacity() < decoded.capacity() {
            *buf = SampleBuffer::new(decoded.capacity() as u64, spec);
        }
        buf.copy_interleaved_ref(decoded);
        let d = down.get_or_insert_with(|| Downsampler::new(spec.rate));
        mono.clear();
        d.push(buf.samples(), spec.channels.count(), &mut mono);
        fp.push(&mono);
    }
    Ok(fp.out)
}

pub fn save(path: &Path, fp: &[u32]) -> Result<(), String> {
    let mut bytes = Vec::with_capacity(4 + fp.len() * 4);
    bytes.extend_from_slice(MAGIC);
    for v in fp {
        bytes.extend_from_slice(&v.to_le_bytes());
    }
    if let Some(dir) = path.parent() {
        std::fs::create_dir_all(dir).map_err(|e| e.to_string())?;
    }
    let tmp = path.with_extension("tmp");
    File::create(&tmp).and_then(|mut f| f.write_all(&bytes)).map_err(|e| e.to_string())?;
    std::fs::rename(&tmp, path).map_err(|e| e.to_string())
}

pub fn load(path: &Path) -> Result<Vec<u32>, String> {
    let mut bytes = Vec::new();
    File::open(path).and_then(|mut f| f.read_to_end(&mut bytes)).map_err(|e| e.to_string())?;
    if bytes.len() < 4 || &bytes[..4] != MAGIC {
        return Err("impronta non valida".into());
    }
    Ok(bytes[4..].chunks_exact(4).map(|c| u32::from_le_bytes([c[0], c[1], c[2], c[3]])).collect())
}

/// Tratto uguale in due impronte: inizio e fine (in passi) in `a` e in `b`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Match {
    pub a_start: usize,
    pub a_end: usize,
    pub b_start: usize,
}

/// Valori che compaiono troppe volte (silenzio, rumore costante) non aiutano ad allineare.
const MAX_HITS: usize = 30;
/// Voti minimi perché uno spostamento tra le due impronte venga verificato.
const MIN_VOTES: usize = 6;
/// Media mobile della differenza in bit: sotto questa soglia i due tratti sono "uguali".
const MAX_BER: f32 = 0.30;
const SMOOTH: usize = 31;

/// Trova i tratti comuni ad `a` e `b` lunghi almeno `min_frames`.
pub fn find_matches(a: &[u32], b: &[u32], min_frames: usize) -> Vec<Match> {
    if a.len() < SMOOTH || b.len() < SMOOTH {
        return Vec::new();
    }
    let mut index: HashMap<u32, Vec<u32>> = HashMap::new();
    for (j, &h) in b.iter().enumerate() {
        index.entry(h).or_default().push(j as u32);
    }
    // Ogni valore uguale "vota" per lo spostamento tra le due posizioni.
    let mut votes: HashMap<i64, (usize, usize, usize)> = HashMap::new();
    // Si cercano anche i valori che differiscono di un solo bit: dopo una ricodifica i valori
    // identici sono pochi, quelli quasi identici molti di più.
    for (i, &h) in a.iter().enumerate() {
        for flip in 0..=BANDS - 1 {
            let key = if flip == BANDS - 1 { h } else { h ^ (1 << flip) };
            let Some(js) = index.get(&key) else { continue };
            if js.len() > MAX_HITS {
                continue;
            }
            for &j in js {
                let e = votes.entry(j as i64 - i as i64).or_insert((0, i, i));
                e.0 += 1;
                e.2 = i;
            }
        }
    }
    let mut offsets: Vec<(i64, (usize, usize, usize))> = votes.into_iter().filter(|(_, v)| v.0 >= MIN_VOTES).collect();
    offsets.sort_by(|x, y| y.1 .0.cmp(&x.1 .0));
    offsets.truncate(200);

    let margin = ms_to_frames(60_000.0);
    let mut found: Vec<Match> = Vec::new();
    for (offset, (_, first, last)) in offsets {
        let lo = first.saturating_sub(margin).max((-offset).max(0) as usize);
        let hi = (last + margin).min(a.len()).min((b.len() as i64 - offset).max(0) as usize);
        if hi <= lo + SMOOTH {
            continue;
        }
        // Errore per passo, tollerando uno slittamento di un passo tra le due codifiche.
        let err: Vec<f32> = (lo..hi)
            .map(|i| {
                let j = i as i64 + offset;
                [j - 1, j, j + 1]
                    .iter()
                    .filter(|&&k| k >= 0 && (k as usize) < b.len())
                    .map(|&k| (a[i] ^ b[k as usize]).count_ones())
                    .min()
                    .unwrap_or(32) as f32
                    / 32.0
            })
            .collect();
        for (s, e) in matched_runs(&err, min_frames) {
            let m = Match { a_start: lo + s, a_end: lo + e, b_start: (lo as i64 + s as i64 + offset) as usize };
            // Lo stesso tratto trovato con uno spostamento vicino: si tiene il più lungo.
            if let Some(o) = found.iter_mut().find(|o| overlap(o.a_start, o.a_end, m.a_start, m.a_end) > 0 && (o.b_start as i64 - o.a_start as i64 - offset).abs() <= 3) {
                if m.a_end - m.a_start > o.a_end - o.a_start {
                    *o = m;
                }
            } else {
                found.push(m);
            }
        }
    }
    found.sort_by_key(|m| m.a_start);
    found
}

/// Tratti in cui la media mobile dell'errore resta sotto la soglia; buchi brevi vengono chiusi.
fn matched_runs(err: &[f32], min_frames: usize) -> Vec<(usize, usize)> {
    let n = err.len();
    let half = SMOOTH / 2;
    let mut prefix = vec![0f32; n + 1];
    for i in 0..n {
        prefix[i + 1] = prefix[i] + err[i];
    }
    let mut runs: Vec<(usize, usize)> = Vec::new();
    let mut start: Option<usize> = None;
    for i in 0..=n {
        let ok = i < n && {
            let lo = i.saturating_sub(half);
            let hi = (i + half + 1).min(n);
            (prefix[hi] - prefix[lo]) / (hi - lo) as f32 <= MAX_BER
        };
        match (ok, start) {
            (true, None) => start = Some(i),
            (false, Some(s)) => {
                runs.push((s, i));
                start = None;
            }
            _ => {}
        }
    }
    let gap = ms_to_frames(2_000.0);
    let mut merged: Vec<(usize, usize)> = Vec::new();
    for r in runs {
        match merged.last_mut() {
            Some(last) if r.0 - last.1 <= gap => last.1 = r.1,
            _ => merged.push(r),
        }
    }
    merged.retain(|(s, e)| e - s >= min_frames);
    merged
}

fn overlap(a0: usize, a1: usize, b0: usize, b1: usize) -> usize {
    a1.min(b1).saturating_sub(a0.max(b0))
}

/// Unisce intervalli sovrapposti o vicini (entro `gap`).
pub fn merge(mut ranges: Vec<(usize, usize)>, gap: usize) -> Vec<(usize, usize)> {
    ranges.sort();
    let mut out: Vec<(usize, usize)> = Vec::new();
    for r in ranges {
        match out.last_mut() {
            Some(last) if r.0 <= last.1 + gap => last.1 = last.1.max(r.1),
            _ => out.push(r),
        }
    }
    out
}

/// Limiti di durata di uno spot.
pub const MIN_AD_MS: f64 = 5_000.0;
pub const MAX_AD_MS: f64 = 300_000.0;

/// Tratti di `a` presenti anche in `b`, con la posizione in `b`: (inizio a, fine a, inizio b, fine b).
pub fn repeated(a: &[u32], b: &[u32]) -> Vec<(usize, usize, usize, usize)> {
    let (min, max) = (ms_to_frames(MIN_AD_MS), ms_to_frames(MAX_AD_MS));
    let matches = find_matches(a, b, min);
    // Se coincidono gran parte dei due file è lo stesso episodio (es. ripubblicato): niente da segnare.
    let covered: usize = merge(matches.iter().map(|m| (m.a_start, m.a_end)).collect(), 0).iter().map(|(s, e)| e - s).sum();
    if covered * 2 > a.len().min(b.len()) {
        return Vec::new();
    }
    matches
        .into_iter()
        .filter(|m| m.a_end - m.a_start <= max)
        .map(|m| (m.a_start, m.a_end, m.b_start, m.b_start + (m.a_end - m.a_start)))
        .collect()
}

/// Tratti di `local` che non compaiono in `other` (un secondo download dello stesso episodio).
pub fn inserted(local: &[u32], other: &[u32]) -> Vec<(usize, usize)> {
    let (min, max) = (ms_to_frames(MIN_AD_MS), ms_to_frames(MAX_AD_MS));
    let content = merge(find_matches(local, other, ms_to_frames(10_000.0)).iter().map(|m| (m.a_start, m.a_end)).collect(), ms_to_frames(1_000.0));
    let covered: usize = content.iter().map(|(s, e)| e - s).sum();
    // I due file devono essere davvero lo stesso episodio.
    if covered * 2 < local.len() {
        return Vec::new();
    }
    let mut gaps = Vec::new();
    let mut pos = 0;
    for (s, e) in content.iter().copied().chain(std::iter::once((local.len(), local.len()))) {
        if s > pos {
            gaps.push((pos, s));
        }
        pos = pos.max(e);
    }
    gaps.retain(|(s, e)| e - s >= min && e - s <= max);
    gaps
}
