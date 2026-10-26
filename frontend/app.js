const state = { menus: [], restaurants: new Map(), generatedAt: null };
const el = Object.fromEntries(["search", "day", "diet", "max-price", "show-sides", "status", "menu-results"].map(id => [id, document.getElementById(id)]));

const markerLabels = {
  VEGETARIAN: "🥕 Vegetarisch",
  VEGAN: "🌱 Vegan",
  ORGANIC: "🌿 Bio",
  BEEF: "🐄 Rind",
  PORK: "🐖 Schwein",
  POULTRY: "🍗 Geflügel",
  LAMB: "🐑 Lamm",
  FISH: "🐟 Fisch",
  GAME_MEAT: "🦌 Wild",
  GLUTEN_FREE: "🌾 Glutenfrei",
  CONTAINS_ALCOHOL: "🍷 Enthält Alkohol",
  MENSA_VITAL: "💚 MensaVital",
  JURADISTL: "🏞️ Juradistl"
};

let viewMode = "list";

function localDateString() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Berlin",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map(({type, value}) => [type, value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function escapeHtml(value = "") {
  return String(value).replace(/[&<>"']/g, c => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[c]);
}

function formatPrice(value) {
  return typeof value === "number"
    ? new Intl.NumberFormat("de-DE", {style: "currency", currency: "EUR"}).format(value)
    : "—";
}

function formatDate(date) {
  return new Intl.DateTimeFormat("de-DE", {
    timeZone: "Europe/Berlin",
    weekday: "long",
    day: "2-digit",
    month: "long",
    year: "numeric"
  }).format(new Date(`${date}T12:00:00+01:00`));
}

function matches(item, restaurant) {
  const query = el.search.value.trim().toLocaleLowerCase("de");
  const searchable = [item.name, item.description, item.category, restaurant?.name]
    .filter(Boolean).join(" ").toLocaleLowerCase("de");
  if (query && !searchable.includes(query)) return false;

  if (el["max-price"].value !== "") {
    const maxPrice = Number(el["max-price"].value);
    const price = item.studentPrice ?? item.price;
    if (typeof price !== "number" || price > maxPrice) return false;
  }

  const dietary = Array.isArray(item.dietary) ? item.dietary : [];
  if (el.diet.value === "vegetarian" && !dietary.includes("VEGETARIAN") && !dietary.includes("VEGAN")) return false;
  if (el.diet.value === "vegan" && !dietary.includes("VEGAN")) return false;
  return true;
}

function renderPrices(item) {
  const prices = [
    ["Studierende", item.studentPrice ?? item.price],
    ["Beschäftigte", item.employeePrice],
    ["Gäste", item.guestPrice]
  ];

  return `<dl class="prices">${prices.map(([label, value]) => `
    <div class="price-tier">
      <dt>${label}</dt>
      <dd${typeof value === "number" ? "" : ' class="price-missing"'}>${formatPrice(value)}</dd>
    </div>`).join("")}
  </dl>`;
}

function renderDish({menu, item, restaurant}) {
  const dietary = Array.isArray(item.dietary) ? item.dietary : [];
  // Vegan schließt vegetarisch bereits ein: zeige dafür nur das spezifischere Vegan-Icon.
  const visibleDietary = dietary.includes("VEGAN")
    ? dietary.filter(marker => marker !== "VEGETARIAN")
    : dietary;
  const markers = visibleDietary.map(marker => markerLabels[marker] ?? marker)
    .map(label => `<span class="tag">${escapeHtml(label)}</span>`).join("");
  const source = menu.sourceUrl
    ? `<a href="${escapeHtml(menu.sourceUrl)}" target="_blank" rel="noopener noreferrer">Original-Speiseplan <span aria-hidden="true">↗</span></a>`
    : "";

  return `<article class="dish-card">
    <div class="dish-heading">
      <div>
        ${item.category ? `<p class="category-label">${escapeHtml(item.category)}</p>` : ""}
        <h3>${escapeHtml(item.name ?? "Gericht ohne Namen")}</h3>
      </div>
    </div>
    ${item.description ? `<p class="dish-description">${escapeHtml(item.description)}</p>` : ""}
    ${renderPrices(item)}
    ${markers ? `<div class="tags" aria-label="Kennzeichnungen">${markers}</div>` : ""}
    <div class="dish-footer">${source}</div>
  </article>`;
}

function render() {
  const today = localDateString();
  const dayGroups = [];

  for (const menu of state.menus) {
    if (menu.status && menu.status !== "CURRENT") continue;
    if (el.day.value === "today" &&
      ((menu.validFrom && menu.validFrom > today) || (menu.validUntil && menu.validUntil < today))) {
      continue;
    }

    const restaurant = state.restaurants.get(menu.restaurantId);
    const items = (menu.items ?? [])
      .map((item, index) => ({menu, item, restaurant, index}))
      .filter(({item}) => el["show-sides"].checked || String(item.category ?? "").trim().toLocaleLowerCase("de") !== "beilagen")
      .filter(({item}) => matches(item, restaurant));

    if (items.length) dayGroups.push({menu, restaurant, items});
  }

  dayGroups.sort((a, b) => (a.menu.validFrom ?? "").localeCompare(b.menu.validFrom ?? ""));
  const totalVisible = dayGroups.reduce((sum, group) => sum + group.items.length, 0);

  el["menu-results"].classList.toggle("card-view", viewMode === "cards");
  el["menu-results"].classList.toggle("list-view", viewMode === "list");

  el["menu-results"].innerHTML = dayGroups.map(({menu, restaurant, items}) => {
    const date = menu.validFrom ? formatDate(menu.validFrom) : "Datum nicht angegeben";
    const cards = items.map(renderDish).join("");
    return `<section class="day-group" aria-label="${escapeHtml(date)}">
      <header class="day-heading">
        <div>
          <p class="day-kicker">${escapeHtml(restaurant?.name ?? "Mittagskarte")}</p>
          <h2>${escapeHtml(date)}</h2>
          ${menu.serviceStart && menu.serviceEnd ? `<p class="menu-meta">Essensausgabe: ${escapeHtml(menu.serviceStart)}–${escapeHtml(menu.serviceEnd)} Uhr</p>` : ""}
          ${menu.offerNote ? `<p class="menu-meta">${escapeHtml(menu.offerNote)}</p>` : ""}
        </div>
        <span class="dish-count">${items.length} ${items.length === 1 ? "Gericht" : "Gerichte"}</span>
      </header>
      <div class="dish-grid">${cards}</div>
    </section>`;
  }).join("");

  if (totalVisible) {
    const dateText = state.generatedAt
      ? new Intl.DateTimeFormat("de-DE", {dateStyle: "short", timeStyle: "short"}).format(new Date(state.generatedAt))
      : "unbekannt";
    el.status.textContent = `${totalVisible} ${totalVisible === 1 ? "Gericht" : "Gerichte"} gefunden · Datenstand: ${dateText}`;
  } else {
    el.status.textContent = el.search.value || el.diet.value !== "all" || el["max-price"].value !== ""
      ? "Keine Gerichte passen zu deinen Filtern."
      : "Für diesen Zeitraum sind derzeit keine Speisepläne verfügbar.";
  }
}

async function loadData() {
  el.status.textContent = "Speisepläne werden geladen …";
  try {
    const responses = await Promise.all([
      fetch("./menus.json", {cache: "no-cache"}),
      fetch("./restaurants.json", {cache: "no-cache"})
    ]);
    if (responses.some(response => !response.ok)) throw new Error("JSON-Daten konnten nicht geladen werden.");

    const [menus, restaurants] = await Promise.all(responses.map(response => response.json()));
    state.menus = Array.isArray(menus.menus) ? menus.menus : [];
    state.generatedAt = menus.generatedAt;
    state.restaurants = new Map((restaurants.restaurants ?? []).map(restaurant => [restaurant.id, restaurant]));
    render();
  } catch (error) {
    el.status.textContent = "Speisepläne sind momentan nicht verfügbar. Bitte versuche es später erneut.";
    console.error(error);
  }
}

for (const button of document.querySelectorAll("[data-view]")) {
  button.addEventListener("click", () => {
    viewMode = button.dataset.view;
    document.querySelectorAll("[data-view]").forEach(option => {
      const active = option === button;
      option.classList.toggle("is-active", active);
      option.setAttribute("aria-pressed", String(active));
    });
    render();
  });
}

for (const id of ["search", "day", "diet", "max-price", "show-sides"]) {
  el[id].addEventListener("input", render);
  el[id].addEventListener("change", render);
}

loadData();
