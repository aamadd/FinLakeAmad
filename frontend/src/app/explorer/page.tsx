"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Database, Play, Table2 } from "lucide-react";
import { amadApi, type ColumnSchema, type SourceSummary, type TableSummary } from "@/lib/amadApi";

export default function SourceExplorerPage() {
  const router = useRouter();
  const [sources, setSources] = useState<SourceSummary[]>([]);
  const [selectedSource, setSelectedSource] = useState<string>("");
  const [tables, setTables] = useState<TableSummary[]>([]);
  const [selectedTable, setSelectedTable] = useState<TableSummary | null>(null);
  const [columns, setColumns] = useState<ColumnSchema[]>([]);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    amadApi.sources().then((list) => {
      setSources(list);
      if (list.length > 0) setSelectedSource(list[0].id);
    }).catch(() => {});
  }, []);

  useEffect(() => {
    if (!selectedSource) return;
    setErr(null);
    setTables([]);
    setSelectedTable(null);
    setColumns([]);
    amadApi.listTables(selectedSource)
      .then((t) => {
        setTables(t);
        if (t.length > 0) setSelectedTable(t[0]);
      })
      .catch((e) => setErr(String(e)));
  }, [selectedSource]);

  useEffect(() => {
    if (!selectedSource || !selectedTable) return;
    amadApi.tableSchema(selectedSource, selectedTable.schema, selectedTable.name)
      .then((res) => setColumns(res.columns))
      .catch(() => setColumns([]));
  }, [selectedSource, selectedTable]);

  const launchExtraction = async () => {
    if (!selectedSource || !selectedTable) return;
    await amadApi.submitJob({
      sourceId: selectedSource,
      schema: selectedTable.schema,
      table: selectedTable.name,
      format: "PARQUET",
      compression: "ZSTD",
      batchSize: 10000,
      partitionBy: ["event_date"],
      destinationPrefix: "curated",
      maxRows: 50000,
    });
    router.push("/jobs");
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-neutral-900">Source Explorer</h1>
        <p className="text-xs text-neutral-500">
          Parcourez les schémas, tables et colonnes des sources connectées avant de lancer une extraction vers MinIO.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <div className="lg:col-span-4 border border-neutral-300 rounded-lg bg-white p-4 space-y-4">
          <div>
            <label className="block text-[11px] font-semibold uppercase tracking-wider text-neutral-400 mb-2">Source</label>
            <select
              value={selectedSource}
              onChange={(e) => setSelectedSource(e.target.value)}
              className="w-full border border-neutral-300 rounded-md px-3 py-2 text-xs bg-white"
            >
              {sources.map((s) => (
                <option key={s.id} value={s.id}>{s.name} ({s.engine})</option>
              ))}
            </select>
          </div>

          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wider text-neutral-400 mb-2">Tables</div>
            {err && <div className="text-xs text-neutral-600 p-2 bg-neutral-100 rounded">{err}</div>}
            <div className="space-y-1">
              {tables.map((t) => {
                const active = selectedTable?.schema === t.schema && selectedTable?.name === t.name;
                return (
                  <button
                    key={`${t.schema}.${t.name}`}
                    onClick={() => setSelectedTable(t)}
                    className={`w-full text-left px-3 py-2 rounded-md text-xs flex items-center justify-between border ${
                      active ? "border-neutral-800 bg-neutral-50 font-semibold" : "border-transparent hover:bg-neutral-50"
                    }`}
                  >
                    <span className="flex items-center gap-2"><Table2 className="h-3.5 w-3.5 text-neutral-500" />{t.schema}.{t.name}</span>
                    <span className="text-[11px] text-neutral-400">{t.estimatedRows.toLocaleString()}</span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        <div className="lg:col-span-8 border border-neutral-300 rounded-lg bg-white overflow-hidden">
          <div className="p-4 border-b border-neutral-200 flex items-center justify-between">
            <div className="flex items-center gap-2 text-xs font-semibold text-neutral-900">
              <Database className="h-4 w-4 text-neutral-500" />
              {selectedTable ? `${selectedTable.schema}.${selectedTable.name}` : "Sélectionnez une table"}
            </div>
            {selectedTable && (
              <button
                onClick={launchExtraction}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-[#27272a] hover:bg-neutral-900 text-white text-xs font-medium"
              >
                <Play className="h-3 w-3" /> Extraire vers MinIO
              </button>
            )}
          </div>
          <table className="w-full text-xs text-left">
            <thead className="bg-neutral-50 text-neutral-500 border-b border-neutral-200">
              <tr><th className="py-2.5 px-4">#</th><th className="py-2.5 px-4">Colonne</th><th className="py-2.5 px-4">Type</th><th className="py-2.5 px-4">Nullable</th></tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {columns.map((c) => (
                <tr key={c.ordinal}>
                  <td className="py-2.5 px-4 text-neutral-400">{c.ordinal}</td>
                  <td className="py-2.5 px-4 font-mono text-neutral-900">{c.name}</td>
                  <td className="py-2.5 px-4 text-neutral-600">{c.dataType}</td>
                  <td className="py-2.5 px-4 text-neutral-500">{c.nullable ? "YES" : "NO"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
