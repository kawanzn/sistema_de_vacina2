#!/usr/bin/env python3
"""Gera CSV de funcionalidades do EasyVacc."""

import csv
from pathlib import Path

from gerar_funcionalidades_pdf import SECTIONS

OUTPUT = Path(__file__).resolve().parent.parent / "docs" / "EasyVacc_Funcionalidades.csv"

LABELS = {"sim": "Sim", "parcial": "Parcial", "nao": "Não"}


def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f, delimiter=";")
        writer.writerow(["Módulo", "Funcionalidade", "Existe", "Funciona", "Será mantida", "Precisa melhorar"])
        for section in SECTIONS:
            for name, exists, works, kept, improve in section["rows"]:
                writer.writerow([
                    section["title"],
                    name,
                    LABELS.get(exists, exists),
                    LABELS.get(works, works),
                    LABELS.get(kept, kept),
                    improve,
                ])
    print(f"CSV gerado: {OUTPUT}")


if __name__ == "__main__":
    main()
