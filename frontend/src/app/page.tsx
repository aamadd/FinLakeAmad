"use client";

import { useEffect, useState } from "react";
import { amadApi, type HealthResponse } from "@/lib/amadApi";

export default function HomePage() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = () => {
    setErr(null);
    amadApi.health().then(setHealth).catch((e) => setErr(String(e)));
  };

  useEffect(load, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">FinLakeAmad Control Plane</h1>
          <p className="text-sm text-slate-400">
            SQL Server &amp; PostgreSQL → Keyset Ingestion → Parquet/CSV (MinIO) → Hive Metastore → Trino
          </p>
        </div>
        <button
          onClick={load}
          className="px-3 py-1.5 rounded bg-slate-800 hover:bg-slate-700 text-sm text-slate-200"
        >
          Refresh
        </button>
      </div>

      {err && <div className="p-4 rounded border border-rose-500/40 bg-rose-950/40 text-rose-200 text-sm">{err}</div>}

      {health && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {Object.entries(health.components).map(([name, state]) => (
            <div key={name} className="p-4 rounded-lg border border-slate-800 bg-slate-900/70">
              <div className="text-xs uppercase tracking-wider text-slate-400">{name}</div>
              <div className={`mt-2 text-lg font-semibold ${state === "UP" ? "text-emerald-400" : "text-rose-400"}`}>
                {state}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
