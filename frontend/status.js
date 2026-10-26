const statusLabels = {
  NOT_YET_AUTOMATED: "Noch nicht protokolliert",
  STALE_SOURCE: "Quelle möglicherweise veraltet",
  NOT_IMPLEMENTED: "Noch nicht implementiert",
  OK: "Erfolgreich",
  WARNING: "Hinweis",
  ERROR: "Fehler",
  NO_DISHES: "Keine Gerichte gefunden",
  DISABLED: "Deaktiviert"
};

function safe(value) {
  return String(value ?? "").replace(/[&<>"']/g, character => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[character]);
}

function formatTimestamp(value) {
  if (!value) return "Noch nicht erfasst";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Zeitpunkt unbekannt";
  return new Intl.DateTimeFormat("de-DE", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Europe/Berlin"
  }).format(date);
}

function pillClass(status) {
  if (status === "OK") return "good";
  if (["STALE_SOURCE", "WARNING", "NO_DISHES"].includes(status)) return "warning";
  return "";
}

async function loadImportStatus() {
  const overall = document.getElementById("overall-status");
  const list = document.getElementById("source-statuses");
  try {
    const response = await fetch("./import-status.json", {cache: "no-cache"});
    if (!response.ok) throw new Error("Statusdatei konnte nicht geladen werden.");
    const data = await response.json();
    overall.innerHTML = `
      <strong>Letzte Statusaktualisierung:</strong> ${safe(formatTimestamp(data.updatedAt))}<br>
      <strong>Letzter erfolgreicher Import (gesamt):</strong> ${safe(formatTimestamp(data.lastImportAt))}<br>
      ${safe(data.summary ?? "")}
    `;
    list.innerHTML = (data.sources ?? []).map(source => {
      const status = statusLabels[source.status] ?? source.status ?? "Unbekannt";
      const dishes = source.dishCount == null ? "Gerichtszahl noch nicht erfasst" : `${source.dishCount} Gerichte gefunden`;
      const menus = source.menuCount == null ? "Menüzahl noch nicht erfasst" : `${source.menuCount} Menüs`;
      return `
        <article class="source-status">
          <div class="source-topline">
            <h2>${safe(source.name ?? source.id ?? "Unbekannte Quelle")}</h2>
            <span class="status-pill ${pillClass(source.status)}">${safe(status)}</span>
          </div>
          <p>${safe(source.message ?? "Kein weiterer Hinweis.")}</p>
          <p class="source-meta">
            Quelle aktiviert: ${source.enabled ? "Ja" : "Nein"} ·
            Letzter Versuch: ${safe(formatTimestamp(source.lastAttemptAt))} ·
            Letzter Erfolg: ${safe(formatTimestamp(source.lastSuccessAt))}
          </p>
          <p class="source-meta">${safe(menus)} · ${safe(dishes)}</p>
        </article>`;
    }).join("");
    if (!(data.sources ?? []).length) list.textContent = "Es sind noch keine Quellen erfasst.";
  } catch (error) {
    overall.textContent = "Der Importstatus ist momentan nicht verfügbar.";
    list.textContent = "";
    console.error(error);
  }
}

loadImportStatus();
