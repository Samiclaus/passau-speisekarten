const state = { menus: [], restaurants: new Map(), generatedAt: null };
const el = Object.fromEntries(["search", "day", "diet", "max-price", "status", "menu-results"].map(id => [id, document.getElementById(id)]));

function localDateString() {
  const parts = new Intl.DateTimeFormat("en-CA", {timeZone:"Europe/Berlin",year:"numeric",month:"2-digit",day:"2-digit"}).formatToParts(new Date());
  const values = Object.fromEntries(parts.map(({type,value}) => [type,value]));
  return `${values.year}-${values.month}-${values.day}`;
}
function escapeHtml(value = "") {
  return String(value).replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
}
function formatPrice(value) {
  return typeof value === "number"
    ? new Intl.NumberFormat("de-DE",{style:"currency",currency:"EUR"}).format(value)
    : "—";
}
function matches(item, restaurant) {
  const query = el.search.value.trim().toLocaleLowerCase("de");
  if (query && ![item.name,item.description,restaurant?.name].filter(Boolean).join(" ").toLocaleLowerCase("de").includes(query)) return false;
  if (el["max-price"].value !== "" && (typeof item.price !== "number" || item.price > Number(el["max-price"].value))) return false;
  const dietary = Array.isArray(item.dietary) ? item.dietary : [];
  if (el.diet.value === "vegetarian" && !dietary.includes("VEGETARIAN") && !dietary.includes("VEGAN")) return false;
  if (el.diet.value === "vegan" && !dietary.includes("VEGAN")) return false;
  return true;
}
function render() {
  const today = localDateString(), visible = [];
  for (const menu of state.menus) {
    if (menu.status && menu.status !== "CURRENT") continue;
    if (el.day.value === "today" && ((menu.validFrom && menu.validFrom > today) || (menu.validUntil && menu.validUntil < today))) continue;
    const restaurant = state.restaurants.get(menu.restaurantId);
    for (const item of menu.items ?? []) if (matches(item, restaurant)) visible.push({menu,item,restaurant});
  }
  visible.sort((a,b) => (a.restaurant?.name ?? "").localeCompare(b.restaurant?.name ?? "","de") || (a.item.name ?? "").localeCompare(b.item.name ?? "","de"));
  el["menu-results"].innerHTML = visible.map(({menu,item,restaurant}) => {
    const studentPrice = item.studentPrice ?? item.price;
    const prices = [
      ["Studierende", studentPrice],
      ["Beschäftigte", item.employeePrice],
      ["Gäste", item.guestPrice]
    ];
    const source = menu.sourceUrl ? `<a href="${escapeHtml(menu.sourceUrl)}" target="_blank" rel="noopener noreferrer">Originalkarte</a>` : "";
    return `<article class="card"><div class="card-title"><h2>${escapeHtml(item.name ?? "Gericht ohne Namen")}</h2></div>
      <p class="restaurant">${escapeHtml(restaurant?.name ?? "Unbekanntes Lokal")}</p>
      ${item.category ? `<p class="muted category">${escapeHtml(item.category)}</p>` : ""}
      ${restaurant?.address ? `<p class="muted">${escapeHtml(restaurant.address)}</p>` : ""}
      ${item.description ? `<p>${escapeHtml(item.description)}</p>` : ""}
      <dl class="prices">${prices.map(([label,value]) => `<div><dt>${label}</dt><dd>${formatPrice(value)}</dd></div>`).join("")}</dl>
      ${item.dietary?.length ? `<p class="muted">${item.dietary.map(escapeHtml).join(" · ")}</p>` : ""}
      <p>${source}</p></article>`;
  }).join("");
  el.status.textContent = visible.length ? `${visible.length} Gericht(e) · Datenstand: ${state.generatedAt ?? "unbekannt"}` : "Keine passenden Gerichte gefunden.";
}
async function loadData() {
  try {
    const responses = await Promise.all([fetch("./menus.json",{cache:"no-cache"}),fetch("./restaurants.json",{cache:"no-cache"})]);
    if (responses.some(r => !r.ok)) throw new Error("JSON-Daten konnten nicht geladen werden.");
    const [menus,restaurants] = await Promise.all(responses.map(r => r.json()));
    state.menus = Array.isArray(menus.menus) ? menus.menus : [];
    state.generatedAt = menus.generatedAt;
    state.restaurants = new Map((restaurants.restaurants ?? []).map(r => [r.id,r]));
    render();
  } catch (error) { el.status.textContent = "Speisekarten sind momentan nicht verfügbar."; console.error(error); }
}
for (const id of ["search","day","diet","max-price"]) {
  el[id].addEventListener("input",render);
  el[id].addEventListener("change",render);
}
loadData();
