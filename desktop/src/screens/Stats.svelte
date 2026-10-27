<script lang="ts">
  import { listeningDb, type ListeningEntry } from "../lib/db.ts";
  import { epochDay, formatDuration } from "../lib/format.ts";
  import { locale, t } from "../lib/i18n.ts";
  import { nav, snackbar, versions, changed } from "../lib/store.svelte.ts";
  import { sync } from "../lib/sync.svelte.ts";
  import Button from "../components/Button.svelte";
  import Cover from "../components/Cover.svelte";
  import Dialog from "../components/Dialog.svelte";
  import Icon from "../components/Icon.svelte";
  import IconButton from "../components/IconButton.svelte";
  import Segmented from "../components/Segmented.svelte";

  type Period = "day" | "week" | "month";
  const DAYS = 14;
  const WEEKS = 12;
  const MONTHS = 12;
  const MIN_ACTIVE_MS = 60_000;

  interface Bar {
    label: string;
    detail: string;
    valueMs: number;
  }

  let period = $state<Period>("day");
  let confirmReset = $state(false);
  let data = $state<{
    totalMs: number;
    completed: number;
    periodMs: number;
    bars: Bar[];
    top: { podcastId: number; title: string; imageUrl: string | null; listenedMs: number }[];
    streak: number;
    best: number;
  } | null>(null);

  function startOf(p: Period): Date {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (p === "day") return new Date(today.getFullYear(), today.getMonth(), today.getDate() - (DAYS - 1));
    if (p === "week") {
      const monday = new Date(today);
      monday.setDate(today.getDate() - ((today.getDay() + 6) % 7));
      monday.setDate(monday.getDate() - 7 * (WEEKS - 1));
      return monday;
    }
    return new Date(today.getFullYear(), today.getMonth() - (MONTHS - 1), 1);
  }

  const short = new Intl.DateTimeFormat(locale, { day: "numeric", month: "short" });
  const narrowDay = new Intl.DateTimeFormat(locale, { weekday: "narrow" });
  const narrowMonth = new Intl.DateTimeFormat(locale, { month: "narrow" });
  const longMonth = new Intl.DateTimeFormat(locale, { month: "long", year: "numeric" });

  function bars(p: Period, days: { day: number; listenedMs: number }[]): Bar[] {
    const byDay = new Map(days.map((d) => [d.day, Number(d.listenedMs)]));
    const start = startOf(p);
    const plusDays = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
    if (p === "day") {
      return Array.from({ length: DAYS }, (_, i) => {
        const d = plusDays(start, i);
        return { label: narrowDay.format(d), detail: short.format(d), valueMs: byDay.get(epochDay(d)) ?? 0 };
      });
    }
    if (p === "week") {
      return Array.from({ length: WEEKS }, (_, i) => {
        const from = plusDays(start, i * 7);
        let sum = 0;
        for (let k = 0; k < 7; k++) sum += byDay.get(epochDay(plusDays(from, k))) ?? 0;
        return { label: String(from.getDate()), detail: `${short.format(from)} – ${short.format(plusDays(from, 6))}`, valueMs: sum };
      });
    }
    return Array.from({ length: MONTHS }, (_, i) => {
      const month = new Date(start.getFullYear(), start.getMonth() + i, 1);
      const daysIn = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
      let sum = 0;
      for (let k = 0; k < daysIn; k++) sum += byDay.get(epochDay(plusDays(month, k))) ?? 0;
      return { label: narrowMonth.format(month), detail: longMonth.format(month), valueMs: sum };
    });
  }

  /** Serie corrente (che termina oggi o ieri) e serie migliore di giorni consecutivi. */
  function streaks(activeDesc: number[]): [number, number] {
    if (!activeDesc.length) return [0, 0];
    const today = epochDay();
    let current = 0;
    if (activeDesc[0] >= today - 1) {
      let expected = activeDesc[0];
      for (const d of activeDesc) {
        if (d !== expected) break;
        current++;
        expected--;
      }
    }
    let best = 1;
    let run = 1;
    for (let i = 1; i < activeDesc.length; i++) {
      run = activeDesc[i] === activeDesc[i - 1] - 1 ? run + 1 : 1;
      best = Math.max(best, run);
    }
    return [current, best];
  }

  $effect(() => {
    void versions.listening;
    const p = period;
    const from = epochDay(startOf(p));
    void Promise.all([
      listeningDb.totals(0),
      listeningDb.days(from),
      listeningDb.topPodcasts(from, 5),
      listeningDb.activeDays(MIN_ACTIVE_MS),
    ]).then(([totals, days, top, active]) => {
      const [streak, best] = streaks(active);
      data = {
        totalMs: totals.listenedMs,
        completed: totals.completed,
        periodMs: days.reduce((a, d) => a + Number(d.listenedMs), 0),
        bars: bars(p, days),
        top,
        streak,
        best,
      };
    });
  });

  const maxBar = $derived(Math.max(1, ...(data?.bars.map((b) => b.valueMs) ?? [1])));
  const maxTop = $derived(Math.max(1, ...(data?.top.map((b) => Number(b.listenedMs)) ?? [1])));

  let backup: ListeningEntry[] = [];
  async function reset() {
    confirmReset = false;
    backup = await listeningDb.all();
    await listeningDb.clear();
    await sync.markStatsReset();
    changed("listening");
    snackbar.show(t("stats_reset_done"), {
      label: t("undo"),
      run: async () => {
        await listeningDb.restore(backup);
        backup = [];
        await sync.cancelStatsReset();
        changed("listening");
        sync.requestSync();
      },
    }, 8000);
  }
</script>

<div class="screen">
  <div class="screen-inner">
    <header class="top-bar">
      <h1>{t("nav_stats")}</h1>
      <IconButton icon="restart_alt" label={t("stats_reset")} onclick={() => (confirmReset = true)} />
    </header>

    {#if data}
      <section class="hero card">
        <div class="label-large">{t("stats_total")}</div>
        <div class="display-small">{formatDuration(data.totalMs)}</div>
      </section>

      <div class="tiles">
        <div class="tile">
          <Icon name="local_fire_department" fill />
          <div class="title-large">{t("days_count", data.streak)}</div>
          <div class="body-small">{t("stats_streak_best", data.best)}</div>
        </div>
        <div class="tile">
          <Icon name="task_alt" />
          <div class="title-large">{data.completed}</div>
          <div class="body-small">{t("stats_completed")}</div>
        </div>
      </div>

      <div class="period">
        <Segmented
          options={["day", "week", "month"] as Period[]}
          selected={period}
          label={(p) => t(p === "day" ? "period_day" : p === "week" ? "period_week" : "period_month")}
          onselect={(p) => (period = p)}
        />
      </div>

      <section class="card chart-card">
        <div class="label-large">{t("stats_period_total")}</div>
        <div class="headline-small">{formatDuration(data.periodMs)}</div>
        {#if data.periodMs === 0}
          <p class="body-medium muted">{t("stats_no_data")}</p>
        {/if}
        <div class="chart">
          {#each data.bars as b, i (i)}
            <div class="col" title="{b.detail}: {formatDuration(b.valueMs)}">
              <div class="bar-wrap">
                <div class="bar" class:zero={b.valueMs === 0} style:height="{Math.max(2, (b.valueMs / maxBar) * 100)}%"></div>
              </div>
              <span class="label-small muted">{b.label}</span>
            </div>
          {/each}
        </div>
      </section>

      {#if data.top.length}
        <h3 class="section-title">{t("stats_top_podcasts")}</h3>
        <div class="top">
          {#each data.top as p (p.podcastId)}
            <button class="top-row" onclick={() => nav.navigate({ name: "podcast", id: p.podcastId })}>
              <Cover src={p.imageUrl} size={48} radius={10} />
              <span class="text">
                <span class="title-small clamp-1">{p.title}</span>
                <span class="meter"><span style:width="{(Number(p.listenedMs) / maxTop) * 100}%"></span></span>
              </span>
              <span class="body-medium muted">{formatDuration(Number(p.listenedMs))}</span>
            </button>
          {/each}
        </div>
      {/if}
    {/if}
  </div>
</div>

{#if confirmReset}
  <Dialog title={t("stats_reset")} onclose={() => (confirmReset = false)}>
    <p>{t("stats_reset_confirm")}</p>
    {#snippet actions()}
      <Button variant="text" onclick={() => (confirmReset = false)}>{t("cancel")}</Button>
      <Button onclick={reset}>{t("stats_reset_action")}</Button>
    {/snippet}
  </Dialog>
{/if}

<style>
  .hero {
    background: var(--md-primary-container);
    color: var(--md-on-primary-container);
    padding: 24px;
    border-radius: 28px;
  }
  .tiles {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    margin: 12px 0 24px;
  }
  .tile {
    background: var(--md-secondary-container);
    color: var(--md-on-secondary-container);
    border-radius: 20px;
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .period {
    margin-bottom: 12px;
  }
  .chart-card {
    border-radius: 24px;
    padding: 20px;
  }
  .chart {
    display: flex;
    align-items: stretch;
    gap: 6px;
    height: 200px;
    margin-top: 16px;
  }
  .col {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    min-width: 0;
  }
  .bar-wrap {
    flex: 1;
    width: 100%;
    display: flex;
    align-items: flex-end;
    justify-content: center;
  }
  .bar {
    width: min(100%, 36px);
    border-radius: 8px 8px 4px 4px;
    background: var(--md-primary);
    transition: height 0.3s ease-out;
  }
  .bar.zero {
    background: var(--md-surface-container-highest);
  }
  .col:hover .bar:not(.zero) {
    background: var(--md-tertiary);
  }
  .top {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .top-row {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 8px 12px;
    border: none;
    background: none;
    border-radius: 16px;
    cursor: pointer;
    text-align: left;
    color: var(--md-on-surface);
  }
  .top-row:hover {
    background: var(--md-surface-container-low);
  }
  .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .meter {
    height: 6px;
    border-radius: 3px;
    background: var(--md-surface-container-highest);
    overflow: hidden;
  }
  .meter span {
    display: block;
    height: 100%;
    background: var(--md-primary);
    border-radius: 3px;
  }
</style>
