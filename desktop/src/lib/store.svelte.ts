// Stato condiviso dell'interfaccia: versioni dei dati, navigazione e messaggi temporanei.

/**
 * Contatori incrementati a ogni modifica dei dati: le schermate li leggono nei loro $effect e si
 * ricaricano quando cambiano.
 */
export const versions = $state({ podcasts: 0, episodes: 0, queue: 0, listening: 0, downloads: 0 });

export function changed(...what: (keyof typeof versions)[]) {
  for (const w of what) versions[w]++;
}

// --- Navigazione ---

export type Route =
  | { name: "library" }
  | { name: "search"; focus?: number }
  | { name: "queue" }
  | { name: "downloads" }
  | { name: "stats" }
  | { name: "history" }
  | { name: "settings" }
  | { name: "podcast"; id: number }
  | { name: "episode"; id: number }
  | { name: "preview"; feedUrl: string; title?: string; image?: string | null };

export const TOP_LEVEL = ["library", "search", "queue", "downloads", "stats", "history", "settings"] as const;

class Navigator {
  stack: Route[] = $state([{ name: "library" }]);
  /** Player a schermo intero aperto sopra la schermata corrente. */
  playerOpen = $state(false);
  /** Incrementato quando si clicca la voce già attiva: le liste tornano in cima. */
  reselect = $state(0);

  get current(): Route {
    return this.stack[this.stack.length - 1];
  }

  get section(): Route["name"] {
    return this.stack[0].name;
  }

  navigate(route: Route) {
    this.playerOpen = false;
    if ((TOP_LEVEL as readonly string[]).includes(route.name)) {
      if (this.stack.length === 1 && this.stack[0].name === route.name) {
        this.reselect++;
        return;
      }
      this.stack = [route];
    } else {
      this.stack = [...this.stack, route];
    }
  }

  /** Sostituisce la schermata corrente (es. anteprima → podcast dopo l'iscrizione). */
  replace(route: Route) {
    this.stack = [...this.stack.slice(0, -1), route];
  }

  back(): boolean {
    if (this.playerOpen) {
      this.playerOpen = false;
      return true;
    }
    if (this.stack.length > 1) {
      this.stack = this.stack.slice(0, -1);
      return true;
    }
    if (this.stack[0].name !== "library") {
      this.stack = [{ name: "library" }];
      return true;
    }
    return false;
  }
}

export const nav = new Navigator();

// --- Messaggi temporanei ---

export interface Snack {
  id: number;
  message: string;
  action?: { label: string; run: () => void };
  duration: number;
}

class Snackbar {
  current: Snack | null = $state(null);
  private timer: ReturnType<typeof setTimeout> | null = null;
  private seq = 0;

  show(message: string, action?: Snack["action"], duration = 4000) {
    if (this.timer) clearTimeout(this.timer);
    this.current = { id: ++this.seq, message, action, duration };
    this.timer = setTimeout(() => this.dismiss(), duration);
  }

  dismiss() {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.current = null;
  }
}

export const snackbar = new Snackbar();
