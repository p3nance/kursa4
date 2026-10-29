#!/usr/bin/env python3
"""Build the manually verified benchmark sidecar and coverage report, without network access."""
import argparse
from collections import Counter
from datetime import date
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def source_ok(source):
    url = urlparse(source)
    host = url.hostname or ""
    return url.scheme == "https" and not url.username and (host == "pugetsystems.com" or host.endswith(".pugetsystems.com"))


def build():
    specs = {p["productId"]: p for p in read("knowledge/component-specs.json")["products"]}
    results, evidence, seen, rejected_sources = [], [], set(), set()
    for filename in ("benchmarks-cpu.json", "benchmarks-gpu.json"):
        fragment = read("knowledge/research/" + filename)
        assert fragment["version"] == 1, filename
        for item in fragment["evidence"]:
            relative = item.get("localPath", item.get("path"))
            path = (ROOT / relative).resolve()
            assert path.is_relative_to(ROOT) and path.is_file(), relative
            assert hashlib.sha256(path.read_bytes()).hexdigest() == item["sha256"], relative
            evidence.append(item)
            if item.get("usage", "").startswith("REJECTED"):
                rejected_sources.add(item["source"])
        for result in fragment["results"]:
            product = specs[result["catalogProductId"]]
            assert product["facts"]["model_id"]["verification"] == "VERIFIED"
            assert product["facts"]["model_id"]["value"] == result["modelId"], result["modelId"]
            if "catalogName" in result:
                assert result["catalogName"] == product["catalogName"], result["modelId"]
            result["catalogName"] = product["catalogName"]
            result["catalogCategory"] = product["catalogCategory"]
            result["dateBasis"] = "PUBLICATION_DATE" if result.get("measuredAt") is None else "MEASUREMENT_DATE"
            if "sourceUpdatedAt" in result:
                result["updatedAt"] = result.pop("sourceUpdatedAt")
            assert result["value"] > 0, result["modelId"]
            assert source_ok(result["source"]) and source_ok(result["chartSource"]), result["modelId"]
            assert result["chartSource"] not in rejected_sources, "A rejected chart cannot supply verified results"
            assert result.get("measuredAt") or result.get("publishedAt"), result["modelId"]
            for key in ("measuredAt", "publishedAt", "updatedAt", "checkedAt"):
                if result.get(key):
                    date.fromisoformat(result[key])
            context = result["context"]
            conditions = context["conditions"]
            if "cohort" in conditions:
                conditions["review_cohort"] = conditions.pop("cohort")
            assert conditions.get("review_cohort"), result["modelId"]
            assert all(isinstance(v, str) and v.strip() for v in conditions.values()), result["modelId"]
            assert context["direction"] in ("HIGHER_IS_BETTER", "LOWER_IS_BETTER")
            assert all(context[key] for key in ("suite", "version", "workload", "metric", "unit"))
            key = (result["modelId"].casefold(), json.dumps(context, sort_keys=True),
                   result["source"], result.get("measuredAt"), result.get("publishedAt"))
            assert key not in seen, f"Duplicate measurement: {key}"
            seen.add(key)
            results.append(result)
    results.sort(key=lambda r: (r["catalogProductId"], r["context"]["suite"], r["context"]["workload"]))
    context_counts = Counter(json.dumps(r["context"], sort_keys=True) for r in results)
    assert all(count >= 2 for count in context_counts.values()), "A comparison cohort needs two models"
    covered = {r["catalogProductId"] for r in results}
    summary = {"results": len(results), "catalogModels": len(covered), "comparisonContexts": len(context_counts)}
    data = {
        "version": 1,
        "checkedAt": "2026-10-25",
        "assembledAt": "2026-10-29",
        "publisher": "Puget Systems",
        "sourcePolicy": "Manual transcription of numeric chart labels from primary laboratory reviews. No recurring scraper or claimed open-data license. Local evidence images are for private verification only.",
        "datePolicy": "Review publication/update dates are retained separately. Exact measurement dates are not published; measuredAt remains null.",
        "comparisonPolicy": "Compare only identical review cohort, platform, workload and conditions. Unpublished details are explicitly unknown and must not join unrelated reviews. Rendering scores do not predict gaming FPS or store-AIB performance.",
        "summary": summary,
        "evidence": evidence,
        "results": results,
    }
    rows = []
    for product_id in sorted(covered):
        samples = [r for r in results if r["catalogProductId"] == product_id]
        scores = "; ".join(f"{r['context']['suite']} {r['context']['version']} {r['context']['workload']}: {r['value']}" for r in samples)
        rows.append(f"| {product_id} | {specs[product_id]['catalogName']} | {scores} |")
    uncovered = [p for p in specs.values() if p["catalogCategory"] in ("Процессоры", "Видеокарты") and p["productId"] not in covered]
    missing = "\n".join(f"- ID{p['productId']}: {p['catalogName']}" for p in sorted(uncovered, key=lambda p: p['productId']))
    report = """# Реальные бенчмарки: источник и покрытие

Первичная сверка 25.10.2026, итоговая интеграция 29.10.2026. Источник — **Puget Systems**, собственные лабораторные обзоры с опубликованными стендами и числовыми подписями диаграмм.
Нормализованы **{results} результата для {models} моделей** каталога, {contexts} контекстов сравнения. Это результаты рендеринга, а не игровые FPS или сводный рейтинг.

Первичные публикации:

- [CPU: Cinema 4D / Cinebench 2024](https://www.pugetsystems.com/labs/articles/cinema-4d-intel-core-14th-gen-vs-amd-ryzen-7000/) — 17.10.2023, обновлено 15.11.2023.
- [Методика и стенды CPU](https://www.pugetsystems.com/labs/articles/14th-gen-intel-core-processors-content-creation-review/).
- [GPU: RTX 40 SUPER Content Creation Review](https://www.pugetsystems.com/labs/articles/nvidia-geforce-rtx-40-super-series-content-creation-review/) — 01.02.2024, обновлено 05.03.2024.

## Границы сравнения

- CPU: шесть моделей, Cinebench 2024 Single Core и Multi Core. Intel и AMD разделены по платформам/BIOS; между ними приложение не рассчитывает процент при несовпадающих условиях.
- GPU: пять моделей в Cinebench 2024 GPU; четыре NVIDIA — в V-Ray 5.02 RTX Mode. AMD с нулевым значением в неподдерживаемом RTX-режиме не импортируется как результат скорости.
- Blender из этого обзора исключён целиком: таблица ПО указывает 4.0, а нижняя подпись графика — v3.6.0. Не установлено, какая версия дала числа; также неоднозначен backend AMD в диаграмме OptiX.
- У каждого контекста есть идентификатор конкретного обзора и стенд. Неопубликованные patch/driver/точные лимиты отмечены явно; они не восстанавливаются по догадке. Сравнение ограничено одной опубликованной лабораторной группой, а не полной независимой воспроизводимостью.
- GPU-образцы — NVIDIA Founders Edition и собственная версия AMD RX 7900 XTX, прямо указанная в Test Setup; точный артикул AMD не опубликован. Точный AIB/SKU магазина неизвестен; эти числа не гарантируют результат складского экземпляра или готовой сборки.
- Точная дата измерения неизвестна: `measuredAt:null`, отдельно `publishedAt`, `updatedAt`, `checkedAt`. В чате показана дата публикации с соответствующей подписью.
- Данные 2023–2024 годов описывают тогдашние версии/драйверы и политику питания. Это исторические измерения, которые нельзя выдавать за новые тесты 2026 года.
- Автоподбор продолжает использовать совместимость/объёмы/цену. Эти результаты не превращают цену в оценку скорости и не задают обещание FPS.

Подробности чисел, стендов и решений: [CPU](cpu.md), [GPU](gpu.md). Полный набор — [benchmarks.json](../../knowledge/benchmarks.json).

## Числовые результаты

Все значения — score/points из конкретного опубликованного теста, больше — лучше. Баллы разных тестов, режимов CPU/GPU и платформ не складываются.

| ID | Модель каталога | Результаты |
| ---: | --- | --- |
{rows}

## Пока без измерений

Для следующих CPU/GPU выбранные статьи не дают подходящих результатов:

{missing}

Другие категории также не получили выдуманных оценок. RAM/SSD требуют точного артикула и отдельного стенда/метрики, а БП/корпуса/кулеры — собственных методик эффективности, шума и температур.

## Проверка и обновление

Числовые подписи сверены по локальным PNG; во фрагментах сохранены URL и SHA256. Копии диаграмм находятся вне ресурсов приложения и не показываются в UI; исключённая диаграмма Blender сохранена как основание отказа. Открытая лицензия набора данных на сайте не заявлена; сохранены отдельные числовые факты с атрибуцией. Автоматического парсинга, регулярного скачивания или публикации исходных диаграмм нет.

```sh
python3 tools/build_benchmarks.py
python3 tools/build_benchmarks.py --check
GRADLE_USER_HOME="$PWD/.gradle-user" ./gradlew build --offline --no-daemon
```

Редактировать результаты после проверки источника в `knowledge/research/benchmarks-*.json`. Сборщик сверяет модель с проверенным справочником, источники, даты, контрольные суммы доказательств, дубликаты и наличие пары в каждом контексте, затем синхронизирует внешний JSON и копию в ресурсах. Он не доказывает достоверность текста страницы автоматически.

Примеры запросов в чате (поддерживаются полные названия и однозначные сокращения):

- `Сравни Intel Core i7-14700K и Intel Core i5-14600K`
- `Сравни AMD Ryzen 9 7950X и AMD Ryzen 9 7900X`
- `Сравни NVIDIA GeForce RTX 4070 SUPER 12GB и NVIDIA GeForce RTX 4070 12GB`
- `14700K vs 14600K`
- `4070 super против 4070`

Чат показывает обычные названия, баллы, разницу `(левое − правое) / правое × 100%` и смысл даты; условия и источники раскрываются отдельно. Знак разницы относится к порядку чисел; направление «лучше» задаётся метрикой отдельно. Если общего контекста или измерения нет, бот называет причину и показывает имеющиеся проверенные характеристики без выдуманной численной разницы. Запрос `сравни i9-14900k и ryzen 7 7800x3d` распознаётся, но измерений 7800X3D в этом наборе нет. Неоднозначные имена требуют выбора, предполагаемая опечатка — подтверждения. Подробности поведения обычного бота — R9 в PLAN.md.
""".format(results=summary["results"], models=summary["catalogModels"], contexts=summary["comparisonContexts"], rows="\n".join(rows), missing=missing)
    body = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    return {"knowledge/benchmarks.json": body,
            "src/main/resources/knowledge/benchmarks.json": body,
            "docs/benchmarks/coverage.md": report}, summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify generated files without writing")
    args = parser.parse_args()
    artifacts, summary = build()
    for relative, content in artifacts.items():
        path = ROOT / relative
        if args.check:
            assert path.read_text(encoding="utf-8") == content, f"Regenerate {relative}"
        else:
            path.write_text(content, encoding="utf-8")
    print(json.dumps(summary))
