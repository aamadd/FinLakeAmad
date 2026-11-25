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
          <h1 className="text-2xl font-bold text-white">Trino SQL Analytics</h1>
          <div className="text-xs text-slate-400">Registered tables in finlake_curated: {tables.join(", ") || "none"}</div>
        </div>
        <button onClick={syncTable} className="px-3 py-1.5 rounded bg-slate-800 hover:bg-slate-700 text-xs">
          Sync Hive Partitions
        </button>
      </div>

      <textarea
        value={sql}
        onChange={(e) => setSql(e.target.value)}
        rows={5}
        className="w-full p-3 rounded-lg bg-slate-950 border border-slate-800 font-mono text-sm text-emerald-200"
      />
      <button onClick={run} className="px-4 py-1.5 rounded bg-emerald-600 hover:bg-emerald-500 text-sm font-medium text-white">
        Run Query
      </button>

      {err && <div className="p-3 rounded border border-rose-500/40 bg-rose-950/40 text-rose-200 text-xs">{err}</div>}

      {result && (
        <div className="rounded-lg border border-slate-800 bg-slate-900/70 overflow-x-auto">
          <div className="px-3 py-2 text-xs text-slate-400 border-b border-slate-800">
            {result.rowCount} rows · {result.durationMs} ms
          </div>
          <table className="w-full text-xs text-left">
            <thead className="text-slate-400 border-b border-slate-800">
              <tr>{result.columns.map((c) => <th key={c} className="p-2">{c}</th>)}</tr>
            </thead>
            <tbody>
              {result.rows.map((r, idx) => (
                <tr key={idx} className="border-b border-slate-800/50">
                  {result.columns.map((c) => <td key={c} className="p-2 font-mono">{String(r[c] ?? "")}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
