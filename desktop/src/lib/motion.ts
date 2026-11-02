import { cubicOut } from "svelte/easing";
import type { TransitionConfig } from "svelte/transition";

/** Curva "emphasized decelerate" di Material 3. */
export const emphasized = (t: number) => 1 - Math.pow(1 - t, 4);

const reduced = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/** Dialog: compare ingrandendosi, sparisce rimpicciolendosi. */
export function pop(_: Element, { duration = 200, start = 0.92 } = {}): TransitionConfig {
  if (reduced()) return { duration: 0 };
  return {
    duration,
    easing: emphasized,
    css: (t) => `opacity: ${Math.min(1, t * 1.6)}; transform: scale(${start + (1 - start) * t});`,
  };
}

/** Menu: si apre "srotolandosi" dal punto in cui si è cliccato. */
export function unfold(_: Element, { duration = 180, up = false } = {}): TransitionConfig {
  if (reduced()) return { duration: 0 };
  return {
    duration,
    easing: cubicOut,
    css: (t) =>
      `opacity: ${t}; transform: ${up ? "translate(-100%, -100%)" : "translateX(-100%)"} scale(${0.85 + 0.15 * t}, ${0.6 + 0.4 * t});`,
  };
}

/** Pannello che entra/esce scorrendo dal basso. */
export function rise(_: Element, { duration = 280, distance = 48 } = {}): TransitionConfig {
  if (reduced()) return { duration: 0 };
  return {
    duration,
    easing: emphasized,
    css: (t, u) => `opacity: ${t}; transform: translateY(${u * distance}px);`,
  };
}

export function fadeFast(_: Element, { duration = 150 } = {}): TransitionConfig {
  if (reduced()) return { duration: 0 };
  return { duration, css: (t) => `opacity: ${t}` };
}

/**
 * Onda Material al clic: un cerchio che si allarga dal punto premuto.
 * Il contenitore dell'onda ha `overflow: hidden` e il raggio dell'elemento, senza tagliarne il contenuto.
 */
export function ripple(node: HTMLElement, opts: { target?: string; disabled?: boolean } = {}) {
  let options = opts;
  function down(e: PointerEvent) {
    if (options.disabled || e.button !== 0 || reduced()) return;
    if ((node as HTMLButtonElement).disabled) return;
    const inner = (e.target as Element | null)?.closest("button, a, input, [role=menu]");
    if (inner && inner !== node && node.contains(inner)) return;
    const host = (options.target ? node.querySelector<HTMLElement>(options.target) : null) ?? node;
    const rect = host.getBoundingClientRect();
    if (getComputedStyle(host).position === "static") host.style.position = "relative";
    const layer = document.createElement("span");
    layer.className = "ripple-layer";
    const wave = document.createElement("span");
    wave.className = "ripple-wave";
    const size = Math.hypot(rect.width, rect.height) * 2;
    const inside = e.clientX >= rect.left && e.clientX <= rect.right && e.clientY >= rect.top && e.clientY <= rect.bottom;
    const x = inside ? e.clientX - rect.left : rect.width / 2;
    const y = inside ? e.clientY - rect.top : rect.height / 2;
    wave.style.width = wave.style.height = `${size}px`;
    wave.style.left = `${x - size / 2}px`;
    wave.style.top = `${y - size / 2}px`;
    layer.appendChild(wave);
    host.appendChild(layer);
    const release = () => {
      wave.classList.add("out");
      setTimeout(() => layer.remove(), 400);
      window.removeEventListener("pointerup", release);
      window.removeEventListener("pointercancel", release);
    };
    window.addEventListener("pointerup", release);
    window.addEventListener("pointercancel", release);
  }
  node.addEventListener("pointerdown", down);
  return {
    update(o: typeof opts = {}) {
      options = o;
    },
    destroy() {
      node.removeEventListener("pointerdown", down);
    },
  };
}
