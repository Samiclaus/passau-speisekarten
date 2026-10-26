#!/usr/bin/env python3
"""Update the public import-status JSON after a manually triggered import."""

import json
import os
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[2]
STATUS_PATH = ROOT / "frontend" / "import-status.json"
MENUS_PATH = ROOT / "frontend" / "menus.json"
BERLIN = ZoneInfo("Europe/Berlin")


def now():
    return datetime.now(BERLIN).isoformat(timespec="seconds")


def load_json(path, fallback):
    if not path.exists():
        return fallback
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main():
    source_id = os.environ["SELECTED_SOURCE"]
    outcome = os.environ.get("IMPORT_OUTCOME", "failure")
    timestamp = now()
    status = load_json(STATUS_PATH, {"schemaVersion": 1, "sources": []})
    menus_doc = load_json(MENUS_PATH, {"menus": []})
    sources = status.setdefault("sources", [])
    source = next((item for item in sources if item.get("id") == source_id), None)
    if source is None:
        source = {"id": source_id, "name": source_id, "enabled": False}
        sources.append(source)

    source["lastAttemptAt"] = timestamp
    matching = [
        menu for menu in menus_doc.get("menus", [])
        if (source_id == "uni-passau-mensa-html" and menu.get("restaurantId") == "uni-passau-mensa")
        or (source_id == "pomeranz" and menu.get("restaurantId") == "pomeranz")
        or (source_id == "isapori" and menu.get("restaurantId") == "isapori")
        or (source_id == "goldenes-schiff" and menu.get("restaurantId") == "goldenes-schiff")
    ]
    source["menuCount"] = len(matching)
    source["dishCount"] = sum(len(menu.get("items", [])) for menu in matching)
    if outcome == "success":
        source["lastSuccessAt"] = timestamp
        if source["dishCount"] == 0:
            source["status"] = "NO_DISHES"
            source["message"] = "Der Importlauf war erfolgreich, aber in den veröffentlichten Menüdaten wurden keine Gerichte für diese Quelle gefunden."
        else:
            source["status"] = "OK"
            source["message"] = f"Import erfolgreich. {source['menuCount']} Menüs und {source['dishCount']} Gerichte sind in den veröffentlichten Daten vorhanden."
        status["lastImportAt"] = timestamp
        status["summary"] = f"Letzter Import von {source.get('name', source_id)}: {source['status']}."
    else:
        source["status"] = "ERROR"
        source["message"] = "Der Import ist fehlgeschlagen. Vorhandene veröffentlichte Menüdateien wurden beibehalten; Details stehen im GitHub-Actions-Lauf."
        status["summary"] = f"Letzter Importversuch von {source.get('name', source_id)} ist fehlgeschlagen. Vorhandene Menüdaten bleiben sichtbar."

    status["updatedAt"] = timestamp
    with STATUS_PATH.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(status, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


if __name__ == "__main__":
    main()
