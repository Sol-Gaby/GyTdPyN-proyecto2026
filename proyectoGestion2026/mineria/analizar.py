"""Calcula las variantes más frecuentes y genera un DFG con PM4Py."""
import argparse
import csv
from collections import Counter, defaultdict
from pathlib import Path


def leer_variantes(archivo):
    casos = defaultdict(list)
    with archivo.open(encoding="utf-8-sig", newline="") as entrada:
        for fila in csv.DictReader(entrada):
            casos[fila["instancia"]].append((int(fila["orden"]), fila["actividad"]))
    return {caso: tuple(actividad for _, actividad in sorted(eventos))
            for caso, eventos in casos.items()}


def guardar_grafo(casos, imagen):
    import pandas as pd
    import pm4py

    tabla = pd.DataFrame(
        (caso, actividad, orden)
        for caso, actividades in casos.items()
        for orden, actividad in enumerate(actividades)
    )
    tabla.columns = ["instancia", "actividad", "orden"]
    # Esta escala preserva el orden exportado. El DFG es de frecuencia, sin duraciones.
    tabla["secuencia"] = pd.to_datetime(tabla["orden"], unit="ms", utc=True)
    datos = pm4py.format_dataframe(
        tabla, case_id="instancia", activity_key="actividad", timestamp_key="secuencia"
    )
    dfg, inicios, finales = pm4py.discover_dfg(datos)
    pm4py.save_vis_dfg(dfg, inicios, finales, str(imagen))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--imagen", type=Path, default=Path("negociacion.png"))
    args = parser.parse_args()
    casos = leer_variantes(args.csv)
    if not casos:
        parser.exit(1, "El CSV no contiene negociaciones concluidas. Generá casos y volvé a exportar.\n")

    variantes = Counter(casos.values())
    print(f"Negociaciones observadas: {len(casos)}. Variantes: {len(variantes)}.")
    for recorrido, cantidad in sorted(variantes.items(), key=lambda par: (-par[1], par[0]))[:3]:
        print(f"{cantidad} ({100 * cantidad / len(casos):.1f} %)  {' → '.join(recorrido)}")
    guardar_grafo(casos, args.imagen)
    print(f"Gráfico guardado en {args.imagen}")


if __name__ == "__main__":
    main()
