"use client";

import { useEffect, useState } from "react";
import { amadApi, type QueryResponse } from "@/lib/amadApi";

const DEFAULT_SQL =
  "SELECT type, isFraud, COUNT(*) AS tx_count, ROUND(SUM(amount), 2) AS total_amount\nFROM amad_lake.finlake_curated.amad_transactions\nGROUP BY type, isFraud\nORDER BY total_amount DESC";

export default function AnalyticsPage() {
  const [tables, setTables] = useState<string[]>([]);
  const [sql, setSql] = useState(DEFAULT_SQL);
  const [result, setResult] = useState<QueryResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    amadApi.catalogTables().then(setTables).catch(() => {});
  }, []);

  const run = async () => {
    setErr(null);
    try {
      setResult(await amadApi.queryTrino(sql, 100));
    } catch (e) {
      setErr(String(e));
    }
  };

  const syncTable = async () => {
    await amadApi.registerTable({
      schema: "finlake_curated",
      table: "amad_transactions",
      partitionedByEventDate: true,
    });
    amadApi.catalogTables().then(setTables).catch(() => {});
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-zinc-900">Trino Analytics</h1>
          <div className="text-xs text-zinc-500">Tables enregistrées (`finlake_curated`): {tables.join(", ") || "aucune"}</div>
        </div>
        <button onClick={syncTable} className="px-3 py-1.5 rounded-lg border border-zinc-200 bg-white hover:bg-zinc-50 text-xs font-medium text-zinc-700">
          Synchroniser Hive Partitions
        </button>
      </div>

      <textarea
        value={sql}
        onChange={(e) => setSql(e.target.value)}
        rows={5}
        className="w-full p-3 rounded-xl bg-white border border-zinc-200 font-mono text-sm text-zinc-900"
      />
      <button onClick={run} className="px-4 py-2 rounded-lg bg-[#18181b] hover:bg-zinc-800 text-sm font-medium text-white">
        Exécuter SQL
      </button>

      {err && <div className="p-3 rounded-lg border border-rose-200 bg-rose-50 text-rose-700 text-xs">{err}</div>}

      {result && (
        <div className="rounded-xl border border-zinc-200 bg-white overflow-x-auto">
          <div className="px-4 py-2.5 text-xs text-zinc-500 border-b border-zinc-200 bg-zinc-50">
            {result.rowCount} lignes · {result.durationMs} ms
          </div>
          <table className="w-full text-xs text-left">
            <thead className="text-zinc-500 border-b border-zinc-200">
              <tr>{result.columns.map((c) => <th key={c} className="p-2.5">{c}</th>)}</tr>
            </thead>
            <tbody className="divide-y divide-zinc-100">
              {result.rows.map((r, idx) => (
                <tr key={idx}>
                  {result.columns.map((c) => <td key={c} className="p-2.5 font-mono text-zinc-800">{String(r[c] ?? "")}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
