"use client";

import { useEffect, useState } from "react";
import { amadApi, type ColumnSchema, type ProbeResponse, type SourceSummary } from "@/lib/amadApi";

export default function SourcesPage() {
  const [sources, setSources] = useState<SourceSummary[]>([]);
  const [probe, setProbe] = useState<Record<string, ProbeResponse>>({});
  const [schemaCols, setSchemaCols] = useState<{ title: string; cols: ColumnSchema[] } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = () => {
    amadApi.sources().then(setSources).catch(() => {});
  };
  useEffect(load, []);

  const runTest = async (id: string) => {
    setBusy(id);
    try {
      const res = await amadApi.testSource(id);
      setProbe((p) => ({ ...p, [id]: res }));
      load();
    } finally {
      setBusy(null);
    }
  };

  const inspect = async (src: SourceSummary, schema: string, table: string) => {
    const res = await amadApi.tableSchema(src.id, schema, table);
    setSchemaCols({ title: `${src.id} · ${schema}.${table}`, cols: res.columns });
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Data Sources</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {sources.map((s) => {
          const p = probe[s.id];
          return (
            <div key={s.id} className="p-5 rounded-lg border border-slate-800 bg-slate-900/70 space-y-4">
              <div className="flex items-start justify-between">
                <div>
                  <div className="font-semibold text-white">{s.name}</div>
                  <div className="text-xs text-slate-400">
                    {s.engine} · {s.host}:{s.port}/{s.database}
                  </div>
                </div>
                <button
                  onClick={() => runTest(s.id)}
                  disabled={busy === s.id}
                  className="px-3 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-xs font-medium text-white disabled:opacity-50"
                >
                  {busy === s.id ? "Testing..." : "4-Step Probe"}
                </button>
              </div>
              {p && (
                <div className="space-y-2 text-xs">
                  {p.steps.map((st) => (
                    <div key={st.step} className="flex justify-between border-b border-slate-800 pb-1">
                      <span>{st.step}. {st.name}</span>
                      <span className={st.status === "PASSED" ? "text-emerald-400" : "text-rose-400"}>
                        {st.status} ({st.durationMs}ms)
                      </span>
                    </div>
                  ))}
                  {p.tables.map((t) => (
                    <button
                      key={`${t.schema}.${t.name}`}
                      onClick={() => inspect(s, t.schema, t.name)}
                      className="mt-1 mr-2 px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200"
                    >
                      Inspect {t.schema}.{t.name} ({t.estimatedRows.toLocaleString()} rows)
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {schemaCols && (
        <div className="p-5 rounded-lg border border-slate-800 bg-slate-900/70">
          <div className="font-semibold text-white mb-3">{schemaCols.title}</div>
          <table className="w-full text-xs text-left">
            <thead className="text-slate-400 border-b border-slate-800">
              <tr><th className="py-1.5">#</th><th>Column</th><th>Type</th><th>Nullable</th></tr>
            </thead>
            <tbody>
              {schemaCols.cols.map((c) => (
                <tr key={c.ordinal} className="border-b border-slate-800/50">
                  <td className="py-1.5 text-slate-400">{c.ordinal}</td>
                  <td className="font-mono text-emerald-300">{c.name}</td>
                  <td>{c.dataType}</td>
                  <td>{c.nullable ? "YES" : "NO"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
