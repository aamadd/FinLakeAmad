"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Database, Plus, Search, Table2, X, Zap } from "lucide-react";
import { amadApi, type ColumnSchema, type SourceSummary, type TableSummary } from "@/lib/amadApi";

type FilterTab = "ALL" | "CONNECTED" | "FAILED" | "UNTESTED";

export default function SourcesPage() {
  const [sources, setSources] = useState<SourceSummary[]>([]);
  const [filter, setFilter] = useState<FilterTab>("ALL");
  const [query, setQuery] = useState("");
  const [testingId, setTestingId] = useState<string | null>(null);
  const [drawerSource, setDrawerSource] = useState<SourceSummary | null>(null);
  const [drawerTables, setDrawerTables] = useState<TableSummary[]>([]);
  const [selectedTable, setSelectedTable] = useState<{ schema: string; name: string; cols: ColumnSchema[] } | null>(null);

  const load = () => {
    amadApi.sources().then(setSources).catch(() => {});
  };
  useEffect(load, []);

  const counts = useMemo(
    () => ({
      ALL: sources.length,
      CONNECTED: sources.filter((s) => s.status === "CONNECTED").length,
      FAILED: sources.filter((s) => s.status === "FAILED").length,
      UNTESTED: sources.filter((s) => s.status === "UNTESTED").length,
    }),
    [sources]
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return sources.filter((s) => {
      if (filter !== "ALL" && s.status !== filter) return false;
      if (!q) return true;
      return (
        s.name.toLowerCase().includes(q) ||
        s.host.toLowerCase().includes(q) ||
        s.database.toLowerCase().includes(q)
      );
    });
  }, [sources, filter, query]);

  const handleTest = async (id: string) => {
    setTestingId(id);
    try {
      await amadApi.testSource(id);
      load();
    } finally {
      setTestingId(null);
    }
  };

  const openExplorer = async (src: SourceSummary) => {
    setDrawerSource(src);
    setSelectedTable(null);
    setDrawerTables([]);
    try {
      const tables = await amadApi.listTables(src.id);
      setDrawerTables(tables);
      if (tables.length > 0) {
        const res = await amadApi.tableSchema(src.id, tables[0].schema, tables[0].name);
        setSelectedTable({ schema: tables[0].schema, name: tables[0].name, cols: res.columns });
      }
    } catch {
      setDrawerTables([]);
    }
  };

  const statusBadge = (status: string) => {
    if (status === "CONNECTED") return <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">Connectée</span>;
    if (status === "FAILED") return <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-rose-50 text-rose-700 border border-rose-200">En échec</span>;
    return <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-zinc-100 text-zinc-600 border border-zinc-200">Non testée</span>;
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-zinc-900">Data Sources</h1>
          <p className="text-sm text-zinc-500">Connectez, testez et explorez vos sources relationnelles (SQL Server &amp; PostgreSQL).</p>
        </div>
        <Link
          href="/sources/new"
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-[#18181b] hover:bg-zinc-800 text-white text-sm font-medium shadow-sm"
        >
          <Plus className="h-4 w-4" />
          Ajouter une source
        </Link>
      </div>

      {/* Wireframe filter pills + search */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          {(
            [
              ["ALL", "Toutes"],
              ["CONNECTED", "Connectées"],
              ["FAILED", "En échec"],
              ["UNTESTED", "Non testées"],
            ] as const
          ).map(([key, label]) => (
            <button
              key={key}
              onClick={() => setFilter(key)}
              className={`px-3 py-1.5 rounded-full text-xs font-medium border transition ${
                filter === key
                  ? "bg-zinc-900 text-white border-zinc-900"
                  : "bg-white text-zinc-600 border-zinc-200 hover:bg-zinc-50"
              }`}
            >
              {label} <span className="ml-1 opacity-75">{counts[key]}</span>
            </button>
          ))}
        </div>

        <div className="relative w-full sm:w-72">
          <Search className="h-4 w-4 text-zinc-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Rechercher par nom, hôte ou base..."
            className="w-full pl-9 pr-3 py-1.5 bg-white border border-zinc-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900/10"
          />
        </div>
      </div>

      {/* Empty states (Wireframe Pages 6 & 7) */}
      {sources.length === 0 ? (
        <div className="bg-white border border-zinc-200 rounded-xl p-12 text-center space-y-3">
          <Database className="h-10 w-10 text-zinc-300 mx-auto" />
          <div className="font-semibold text-zinc-900">Aucune source de données configurée</div>
          <p className="text-sm text-zinc-500 max-w-md mx-auto">Ajoutez votre première connexion SQL Server ou PostgreSQL pour explorer les tables et lancer des jobs d&apos;ingestion.</p>
          <Link href="/sources/new" className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-[#18181b] text-white text-sm font-medium">
            <Plus className="h-4 w-4" /> Ajouter une source
          </Link>
        </div>
      ) : filtered.length === 0 ? (
        <div className="bg-white border border-zinc-200 rounded-xl p-12 text-center space-y-3">
          <Search className="h-8 w-8 text-zinc-300 mx-auto" />
          <div className="font-semibold text-zinc-900">Aucun résultat pour « {query} »</div>
          <button onClick={() => { setQuery(""); setFilter("ALL"); }} className="px-3 py-1.5 rounded-lg border border-zinc-200 text-xs font-medium text-zinc-700 hover:bg-zinc-50">
            Effacer la recherche
          </button>
        </div>
      ) : (
        <div className="bg-white border border-zinc-200 rounded-xl divide-y divide-zinc-100">
          {filtered.map((s) => (
            <div key={s.id} className="p-5 flex flex-wrap items-center justify-between gap-4">
              <div className="space-y-1">
                <div className="flex items-center gap-2.5">
                  <span className="font-semibold text-zinc-900">{s.name}</span>
                  <span className="px-2 py-0.5 rounded bg-zinc-100 text-zinc-600 text-[11px] font-mono">{s.engine}</span>
                  {statusBadge(s.status)}
                </div>
                <div className="text-xs text-zinc-500">
                  {s.host}:{s.port} · Base <span className="font-mono text-zinc-700">{s.database}</span>
                  {s.serverVersion ? ` · ${s.serverVersion}` : ""}
                </div>
                {s.status === "FAILED" && s.lastError && (
                  <div className="text-xs text-rose-600">{s.lastError}</div>
                )}
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleTest(s.id)}
                  disabled={testingId === s.id}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-zinc-200 bg-white hover:bg-zinc-50 text-xs font-medium text-zinc-700 disabled:opacity-50"
                >
                  <Zap className="h-3.5 w-3.5 text-amber-500" />
                  {testingId === s.id ? "Test..." : "Tester"}
                </button>
                <button
                  onClick={() => openExplorer(s)}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-zinc-900 hover:bg-zinc-800 text-xs font-medium text-white"
                >
                  <Table2 className="h-3.5 w-3.5" />
                  Explorer
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Wireframe Explorer Slide-Over Drawer */}
      {drawerSource && (
        <div className="fixed inset-0 z-40 flex justify-end bg-black/25">
          <div className="w-full max-w-xl bg-white h-full shadow-2xl border-l border-zinc-200 flex flex-col">
            <div className="p-5 border-b border-zinc-200 flex items-center justify-between">
              <div>
                <div className="font-semibold text-zinc-900">{drawerSource.name}</div>
                <div className="text-xs text-zinc-500">{drawerSource.engine} · {drawerSource.host}:{drawerSource.port}/{drawerSource.database}</div>
              </div>
              <button onClick={() => setDrawerSource(null)} className="p-1.5 rounded-lg hover:bg-zinc-100">
                <X className="h-4 w-4 text-zinc-500" />
              </button>
            </div>
            <div className="p-5 flex-1 overflow-y-auto space-y-5">
              <div>
                <div className="text-xs font-semibold uppercase tracking-wider text-zinc-400 mb-2">Tables découvertes</div>
                <div className="flex flex-wrap gap-2">
                  {drawerTables.map((t) => (
                    <button
                      key={`${t.schema}.${t.name}`}
                      onClick={async () => {
                        const res = await amadApi.tableSchema(drawerSource.id, t.schema, t.name);
                        setSelectedTable({ schema: t.schema, name: t.name, cols: res.columns });
                      }}
                      className="px-3 py-1.5 rounded-lg border border-zinc-200 text-xs font-medium hover:bg-zinc-50"
                    >
                      {t.schema}.{t.name} ({t.estimatedRows.toLocaleString()} lignes)
                    </button>
                  ))}
                </div>
              </div>

              {selectedTable && (
                <div>
                  <div className="text-xs font-semibold uppercase tracking-wider text-zinc-400 mb-2">
                    Schéma · {selectedTable.schema}.{selectedTable.name}
                  </div>
                  <table className="w-full text-xs text-left border border-zinc-200 rounded-lg overflow-hidden">
                    <thead className="bg-zinc-50 text-zinc-500 border-b border-zinc-200">
                      <tr><th className="p-2">#</th><th className="p-2">Colonne</th><th className="p-2">Type</th><th className="p-2">Nullable</th></tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-100">
                      {selectedTable.cols.map((c) => (
                        <tr key={c.ordinal}>
                          <td className="p-2 text-zinc-400">{c.ordinal}</td>
                          <td className="p-2 font-mono text-zinc-900">{c.name}</td>
                          <td className="p-2 text-zinc-600">{c.dataType}</td>
                          <td className="p-2 text-zinc-500">{c.nullable ? "YES" : "NO"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
