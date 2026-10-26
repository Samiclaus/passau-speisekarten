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
  MEAT: "🥩 Fleisch (nicht näher bestimmt)",
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

function addDays(dateString, days) {
  const date = new Date(`${dateString}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function calendarWeekRange(today) {
  const weekday = new Date(`${today}T12:00:00Z`).getUTCDay();
  const daysSinceMonday = (weekday + 6) % 7;
  const start = addDays(today, -daysSinceMonday);
  return {start, end: addDays(start, 6)};
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

function matches(rawItem, restaurant) {
  const item = normalizedItem(rawItem);
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
  const hasStudentDiscount = typeof item.studentPrice === "number" &&
    ((typeof item.employeePrice === "number" && item.studentPrice < item.employeePrice) ||
     (typeof item.guestPrice === "number" && item.studentPrice < item.guestPrice));

  if (!hasStudentDiscount) {
    const price = item.price ?? item.studentPrice ?? item.employeePrice ?? item.guestPrice;
    return `<dl class="prices prices-single"><div class="price-tier"><dt>Preis</dt><dd${typeof price === "number" ? "" : ' class="price-missing"'}>${formatPrice(price)}</dd></div></dl>`;
  }

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

function normalizedItem(item) {
  const category = String(item.category ?? "").trim();
  const dietary = Array.isArray(item.dietary) ? [...item.dietary] : [];
  if (/vegetarisch/i.test(String(item.name ?? "") + " " + category)) {
    if (!dietary.includes("VEGETARIAN")) dietary.push("VEGETARIAN");
  }

  // Keep dietary properties separate from dish categories and normalize legacy values.
  const normalizedCategory = /^vegetarisch$/i.test(category) || /^hauptgericht$/i.test(category)
    ? "Hauptgerichte"
    : /^suppe$/i.test(category) ? "Suppen"
    : /^vorspeise$/i.test(category) ? "Vorspeisen"
    : /^nachspeise$/i.test(category) ? "Nachspeisen"
    : category;
  return {...item, category: normalizedCategory, dietary};
}

function renderDish({menu, item: rawItem, restaurant}) {
  const item = normalizedItem(rawItem);
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
  const week = calendarWeekRange(today);
  const menusByDay = new Map();

  for (const menu of state.menus) {
    if (menu.status && menu.status !== "CURRENT") continue;
    const validFrom = menu.validFrom ?? "";
    const validUntil = menu.validUntil ?? validFrom;
    if (el.day.value === "today" &&
      ((validFrom && validFrom > today) || (validUntil && validUntil < today))) continue;
    if (el.day.value === "week" &&
      ((validFrom && validFrom > week.end) || (validUntil && validUntil < week.start))) continue;
    if (el.day.value === "future" && validUntil && validUntil < today) continue;

    const restaurant = state.restaurants.get(menu.restaurantId);
    const items = (menu.items ?? [])
      .map((item, index) => ({menu, item, restaurant, index}))
      .filter(({item}) => el["show-sides"].checked || String(item.category ?? "").trim().toLocaleLowerCase("de") !== "beilagen")
      .filter(({item}) => matches(item, restaurant));
    if (!items.length) continue;

    const dateKey = validFrom || "undated";
    if (!menusByDay.has(dateKey)) menusByDay.set(dateKey, {date: validFrom, restaurants: new Map()});
    const day = menusByDay.get(dateKey);
    const restaurantKey = menu.restaurantId ?? restaurant?.name ?? "unknown";
    if (!day.restaurants.has(restaurantKey)) day.restaurants.set(restaurantKey, {menu, restaurant, items: []});
    day.restaurants.get(restaurantKey).items.push(...items);
  }

  const dayGroups = [...menusByDay.values()].sort((a, b) => (a.date || "").localeCompare(b.date || ""));
  const totalVisible = dayGroups.reduce((sum, day) =>
    sum + [...day.restaurants.values()].reduce((subtotal, group) => subtotal + group.items.length, 0), 0);

  el["menu-results"].classList.toggle("card-view", viewMode === "cards");
  el["menu-results"].classList.toggle("list-view", viewMode === "list");

  el["menu-results"].innerHTML = dayGroups.map(day => {
    const date = day.date ? formatDate(day.date) : "Datum nicht angegeben";
    const restaurantGroups = [...day.restaurants.values()]
      .sort((a, b) => (a.restaurant?.name ?? "").localeCompare(b.restaurant?.name ?? "", "de"));
    const dayCount = restaurantGroups.reduce((sum, group) => sum + group.items.length, 0);
    const restaurantsHtml = restaurantGroups.map(({menu, restaurant, items}) => {
      const cards = items.map(renderDish).join("");
      return `<section class="restaurant-group" aria-label="${escapeHtml(restaurant?.name ?? "Mittagskarte")}">
        <header class="restaurant-heading">
          <h3>${escapeHtml(restaurant?.name ?? "Mittagskarte")}</h3>
          <span class="restaurant-dish-count">${items.length} ${items.length === 1 ? "Gericht" : "Gerichte"}</span>
        </header>
        ${menu.serviceStart && menu.serviceEnd ? `<p class="menu-meta">Essensausgabe: ${escapeHtml(menu.serviceStart)}–${escapeHtml(menu.serviceEnd)} Uhr</p>` : ""}
        ${menu.offerNote ? `<p class="menu-meta">${escapeHtml(menu.offerNote)}</p>` : ""}
        <div class="dish-grid">${cards}</div>
      </section>`;
    }).join("");
    return `<section class="day-group" aria-label="${escapeHtml(date)}">
      <header class="day-heading">
        <h2>${escapeHtml(date)}</h2>
        <span class="dish-count">${dayCount} ${dayCount === 1 ? "Gericht" : "Gerichte"}</span>
      </header>
      ${restaurantsHtml}
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
