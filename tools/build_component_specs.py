#!/usr/bin/env python3
"""Rebuild the local, researched catalog sidecar. No network or database writes."""
import argparse
from collections import Counter
from datetime import date
import json
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
CATEGORIES = {
    "Процессоры", "Материнские платы", "Видеокарты", "Оперативная память",
    "SSD-накопители", "Блоки питания", "Корпуса", "Охлаждение",
}
OFFICIAL_DOMAINS = (
    "intel.com", "amd.com", "asus.com", "msi.com", "asrock.com", "gigabyte.com",
    "nvidia.com", "kingston.com", "gskill.com", "corsair.com", "patriotmemory.com",
    "samsung.com", "sandisk.com", "crucial.com", "micron.com", "bequiet.com",
    "fractal-design.com", "nzxt.com", "deepcool.com", "coolermaster.com", "lian-li.com",
    "seasonic.com", "noctua.at", "phanteks.com", "phanteks.com.tw",
)


def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def official_source(source):
    url = urlparse(source)
    return url.scheme == "https" and url.hostname and any(
        url.hostname == domain or url.hostname.endswith("." + domain)
        for domain in OFFICIAL_DOMAINS
    )


def build():
    snapshot = read("knowledge/catalog-reference.json")
    catalog = {p["id"]: p for p in snapshot["products"] if p["category"] in CATEGORIES}
    products = []
    seen = set()
    for filename in ("cpu-boards.json", "gpu-ram-storage.json", "power-cases-cooling.json"):
        fragment = read("knowledge/research/" + filename)
        assert fragment["version"] == 1, filename
        for product in fragment["products"]:
            product_id = product["productId"]
            assert product_id in catalog and product_id not in seen, f"Unexpected/duplicate ID {product_id}"
            row = catalog[product_id]
            assert product["catalogName"] == row["name"], f"Identity mismatch {product_id}"
            seen.add(product_id)
            product["catalogCategory"] = row["category"]
            for key, fact in product["facts"].items():
                assert fact["verification"] in ("VERIFIED", "IMPORTED", "UNKNOWN"), (product_id, key)
                assert official_source(fact["source"]), (product_id, key, fact["source"])
                date.fromisoformat(fact["checkedAt"])
                assert fact.get("evidence"), (product_id, key)
            products.append(product)
    assert seen == set(catalog), f"Missing IDs: {sorted(set(catalog) - seen)}"
    products.sort(key=lambda p: p["productId"])
    facts = [f for p in products for f in p["facts"].values()]
    counts = Counter(f["verification"] for f in facts)
    with_verified = sum(any(f["verification"] == "VERIFIED" for f in p["facts"].values()) for p in products)
    data = {
        "version": 1,
        "checkedAt": snapshot["retrievedAt"],
        "scope": "Local manufacturer-verified facts bound to the current catalog identity. Not a full compatibility certificate.",
        "sourcePolicy": "Official primary sources read on checkedAt. Revision, kit, BIOS and physical configuration uncertainties remain unverified; TDP is not maximum electrical power.",
        "catalogReference": "knowledge/catalog-reference.json",
        "summary": {"productsReviewed": len(products), "productsWithVerifiedFacts": with_verified,
                    "verifiedFacts": counts["VERIFIED"], "importedFacts": counts["IMPORTED"]},
        "products": products,
    }
    category_lines = []
    for category in CATEGORIES:
        group = [p for p in products if p["catalogCategory"] == category]
        category_lines.append((min(p["productId"] for p in group),
                               f"| {category} | {len(group)} | {sum(f['verification'] == 'VERIFIED' for p in group for f in p['facts'].values())} |"))
    table = "\n".join(line for _, line in sorted(category_lines))
    rows = "\n".join(
        f"| {p['productId']} | {p['catalogName']} | "
        f"{sum(f['verification'] == 'VERIFIED' for f in p['facts'].values())} | "
        f"{sum(f['verification'] != 'VERIFIED' for f in p['facts'].values())} |"
        for p in products
    )
    report = f"""# Покрытие проверенных характеристик

Сверка: {snapshot['retrievedAt']}. Локальная копия публичного каталога: {len(snapshot['products'])} товаров.
Из них {len(products)} позиций входят в восемь категорий системного блока; у {with_verified} есть проверенные факты.
Всего **{counts['VERIFIED']} VERIFIED** и {counts['IMPORTED']} явно непроверенных IMPORTED фактов. Пустые/отсутствующие поля не считаются проверенными.

| Категория | Рассмотрено товаров | Проверенных фактов |
| --- | ---: | ---: |
{table}

## Что используется

`knowledge/component-specs.json` читается Java-клиентом поверх описаний Supabase; карточки в БД не переписываются.
Каждый факт содержит значение, официальный источник, статус, дату сверки и основание. `catalogName` и `catalogCategory` защищают от переноса фактов на другую модель под прежним ID.
Нормализованы интерфейсы `NVMe`/`SATA`, форматы `ATX`/`Micro-ATX`/`Mini-ITX`, поколения DDR и сокеты. Ёмкость SSD — десятичные ГБ; у GPU `capacity_gb` означает VRAM и не участвует в минимуме системной RAM.

Полные источники и ограничения по каждой позиции:

- [CPU и платы](cpu-boards.md)
- [GPU, память, SSD](gpu-ram-storage.md)
- [БП, корпуса, охлаждение](power-cases-cooling.md)
- [Загружаемый JSON](../../knowledge/component-specs.json)

## Что требует уточнения

- У GPU нет производителя платы и точного артикула: размеры, разъёмы и максимальное электрическое потребление неизвестны. Минимум БП — рекомендация производителя для эталонной системы; её выполнение не подтверждает всё питание сборки.
- Версия установленного BIOS неизвестна. Совпадение сокета не заменяет проверку поддержки конкретного CPU.
- Для AMD не переносили TDP в максимальное электрическое потребление. Тепловые рейтинги кулеров не сравниваются с электрическим максимумом CPU.
- H5/H7 Flow не имеют года/артикула. Перенесены только общие для проверенных поколений факты; спорные размеры оставлены неизвестными. Для остальных корпусов учитываются ограничения радиаторов и компоновки.
- NH-D15 имеет разные официальные значения высоты для разных страниц/ревизий; старые 165 мм остаются IMPORTED. Штатные размеры воздушных кулеров также зависят от положения вентилятора над RAM.
- Комплекты креплений старых NH-D15, Dark Rock Pro 4, AK620 и H150i Elite Capellix не установлены: списки сокетов IMPORTED. Крепление и полный объём радиатора СЖО требуют ручной проверки.
- Ревизии ряда БП не указаны; наборы/количество/мощность кабелей не считаются подтверждёнными. Форматы корпусов относятся к текущим платам ATX/Micro-ATX/Mini-ITX; условия для E-ATX нужно проверять отдельно.
- Crucial MX500 (ID43): официальные страницы и PDF не удалось прочитать; найденные в поисковом индексе значения не повышены до VERIFIED.
- Периферия и HDD ({len(snapshot['products']) - len(products)} позиций) не входят в текущую модель подбора. Реальные бенчмарки добавлены отдельно; их покрытие — [docs/benchmarks/coverage.md](../benchmarks/coverage.md).

Автоподбор отбрасывает подтверждённые конфликты и применяет подтверждённые минимумы объёма. Полные сборки текущего каталога остаются черновиками `NEEDS_VERIFICATION`, пока неизвестные условия не проверены. Цена не служит оценкой производительности.

## Позиции каталога

| ID | Название | VERIFIED | IMPORTED/UNKNOWN |
| ---: | --- | ---: | ---: |
{rows}

Последний столбец считает только сохранённые непроверенные факты, а не все отсутствующие поля. Причины отсутствия находятся в `unknownFields`/`notes` JSON и отчётах выше.

## Воспроизведение

После ручной сверки официальных документов редактировать `knowledge/research/*.json`, затем выполнить:

```sh
python3 tools/build_component_specs.py
python3 tools/build_component_specs.py --check
GRADLE_USER_HOME="$PWD/.gradle-user" ./gradlew build --offline --no-daemon
```

Сборщик проверяет полноту покрытия снимка, уникальность ID, точные имена, происхождение ссылок и даты; Java-тесты проверяют типы и предметные сценарии. Проверка домена ссылки не является автоматической проверкой достоверности документа. Снимок каталога используется в тестах, а приложение получает актуальные цены и остатки из Supabase.
"""
    body = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    return {
        "knowledge/component-specs.json": body,
        "src/main/resources/knowledge/component-specs.json": body,
        "docs/specs/coverage.md": report,
    }, data["summary"]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check that generated files are current; do not write")
    args = parser.parse_args()
    artifacts, summary = build()
    for relative, content in artifacts.items():
        path = ROOT / relative
        if args.check:
            assert path.read_text(encoding="utf-8") == content, f"Regenerate {relative}"
        else:
            path.write_text(content, encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))
