"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Check, Database, Loader2, Minus, Plus, Search, X, Zap } from "lucide-react";
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

  const formatDate = (iso: string | null) => {
    if (!iso) return "Jamais";
    return new Date(iso).toLocaleString("fr-FR", {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const renderStatus = (s: SourceSummary) => {
    if (testingId === s.id) {
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-neutral-100 text-neutral-700 text-xs font-medium">
          <Loader2 className="h-3 w-3 animate-spin" /> Test en cours
        </span>
      );
    }
    if (s.status === "CONNECTED") {
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-neutral-100 text-neutral-800 text-xs font-medium">
          <Check className="h-3 w-3" /> Connectée
        </span>
      );
    }
    if (s.status === "FAILED") {
      return (
        <div className="space-y-1">
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-neutral-100 text-neutral-900 text-xs font-semibold">
            <X className="h-3 w-3" /> Échec
          </span>
          {s.lastError && <div className="text-[11px] text-neutral-500 max-w-[160px] leading-tight">{s.lastError}</div>}
        </div>
      );
    }
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-neutral-100 text-neutral-600 text-xs font-medium">
        <Minus className="h-3 w-3" /> Non testée
      </span>
    );
  };

  return (
    <div className="space-y-6">
      {/* Header matching Wireframe Page 1 */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold tracking-tight text-neutral-900">Data Sources</h1>
          <p className="text-xs text-neutral-500 max-w-xl">
            Connectez des bases de données externes pour explorer leurs schémas et leurs tables, puis les extraire vers le stockage.
          </p>
        </div>
        <Link
          href="/sources/new"
          className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-[#27272a] hover:bg-neutral-900 text-white text-xs font-medium"
        >
          <Plus className="h-3.5 w-3.5" />
          Ajouter une source
        </Link>
      </div>

      {/* Empty state (Wireframe Page 6) */}
      {sources.length === 0 ? (
        <div className="border border-neutral-300 rounded-lg bg-white py-20 px-6 text-center space-y-4">
          <div className="h-11 w-11 rounded-full bg-neutral-100 flex items-center justify-center mx-auto">
            <Database className="h-5 w-5 text-neutral-500" />
          </div>
          <div className="space-y-1">
            <div className="text-sm font-semibold text-neutral-900">Aucune source de données pour l&apos;instant</div>
            <p className="text-xs text-neutral-500 max-w-md mx-auto">
              Connectez votre première base SQL Server ou PostgreSQL pour explorer ses tables et lancer des extractions.
            </p>
          </div>
          <Link
            href="/sources/new"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-[#27272a] hover:bg-neutral-900 text-white text-xs font-medium"
          >
            <Plus className="h-3.5 w-3.5" />
            Ajouter une source
          </Link>
        </div>
      ) : (
        <>
          {/* Search + Rectangular Filter Pills (Wireframe Page 1 & 7) */}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="relative w-full sm:w-80">
              <Search className="h-3.5 w-3.5 text-neutral-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Rechercher par nom, hôte ou base"
                className="w-full pl-8 pr-3 py-1.5 bg-white border border-neutral-300 rounded-md text-xs text-neutral-900 placeholder:text-neutral-400 focus:outline-none focus:border-neutral-500"
              />
            </div>

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
                  className={`px-3 py-1.5 rounded-md text-xs border transition ${
                    filter === key
                      ? "bg-white border-neutral-800 text-neutral-900 font-semibold"
                      : "bg-white border-neutral-300 text-neutral-600 hover:bg-neutral-50"
                  }`}
                >
                  {label} <span className="ml-1 text-neutral-400">{counts[key]}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Empty search state (Wireframe Page 7) vs Table (Wireframe Page 1) */}
          {filtered.length === 0 ? (
            <div className="border border-neutral-300 rounded-lg bg-white overflow-hidden">
              <div className="py-16 px-6 text-center space-y-3">
                <div className="h-10 w-10 rounded-full bg-neutral-100 flex items-center justify-center mx-auto">
                  <Search className="h-4 w-4 text-neutral-500" />
                </div>
                <div className="text-sm font-semibold text-neutral-900">
                  Aucune source ne correspond à « {query || filter} »
                </div>
                <p className="text-xs text-neutral-500 max-w-md mx-auto">
                  Vérifiez l&apos;orthographe ou effacez la recherche. La recherche porte sur le nom de la connexion, l&apos;hôte et la base de données.
                </p>
                <button
                  onClick={() => { setQuery(""); setFilter("ALL"); }}
                  className="px-3.5 py-1.5 rounded-md border border-neutral-300 bg-white hover:bg-neutral-50 text-xs font-medium text-neutral-800"
                >
                  Effacer la recherche
                </button>
              </div>
              <div className="px-4 py-2.5 border-t border-neutral-200 text-[11px] text-neutral-500">
                0 source sur {sources.length} affichée
              </div>
            </div>
          ) : (
            <div className="border border-neutral-300 rounded-lg bg-white overflow-hidden">
              <table className="w-full text-xs text-left">
                <thead className="border-b border-neutral-200 text-neutral-500 bg-white">
                  <tr>
                    <th className="py-3 px-4 font-medium">Type</th>
                    <th className="py-3 px-4 font-medium">Nom de la connexion</th>
                    <th className="py-3 px-4 font-medium">Hôte / Serveur</th>
                    <th className="py-3 px-4 font-medium">Base de données</th>
                    <th className="py-3 px-4 font-medium">Statut</th>
                    <th className="py-3 px-4 font-medium">Dernier test</th>
                    <th className="py-3 px-4 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-200">
                  {filtered.map((s) => (
                    <tr key={s.id} className="hover:bg-neutral-50/70">
                      <td className="py-3.5 px-4 text-neutral-700 whitespace-nowrap">
                        <span className="inline-flex items-center gap-1.5">
                          <Database className="h-3.5 w-3.5 text-neutral-500" />
                          {s.engine === "MSSQL" ? "SQL Server" : "PostgreSQL"}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 font-semibold text-neutral-900">{s.name}</td>
                      <td className="py-3.5 px-4 font-mono text-neutral-700">{s.host}:{s.port}</td>
                      <td className="py-3.5 px-4 text-neutral-800">{s.database}</td>
                      <td className="py-3.5 px-4">{renderStatus(s)}</td>
                      <td className="py-3.5 px-4 text-neutral-600 whitespace-nowrap">{formatDate(s.lastTestedAt)}</td>
                      <td className="py-3.5 px-4 text-right whitespace-nowrap">
                        <div className="inline-flex items-center gap-2">
                          <button
                            onClick={() => handleTest(s.id)}
                            disabled={testingId === s.id}
                            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md border border-neutral-300 bg-white hover:bg-neutral-50 text-xs font-medium text-neutral-800 disabled:opacity-50"
                          >
                            <Zap className="h-3 w-3" />
                            {testingId === s.id ? "Test..." : "Tester"}
                          </button>
                          <button
                            onClick={() => openExplorer(s)}
                            className="px-2.5 py-1 rounded-md border border-neutral-300 bg-white hover:bg-neutral-50 text-xs font-medium text-neutral-800"
                          >
                            Explorer
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="px-4 py-2.5 border-t border-neutral-200 flex items-center justify-between text-[11px] text-neutral-500">
                <span>{filtered.length} source(s) sur {sources.length} affichée(s)</span>
                <span>Les statuts reflètent le dernier test</span>
              </div>
            </div>
          )}
        </>
      )}

      {/* Explorer Slide-Over Drawer */}
      {drawerSource && (
        <div className="fixed inset-0 z-40 flex justify-end bg-black/20">
          <div className="w-full max-w-xl bg-white h-full shadow-2xl border-l border-neutral-200 flex flex-col">
            <div className="p-5 border-b border-neutral-200 flex items-center justify-between">
              <div>
                <div className="font-semibold text-sm text-neutral-900">{drawerSource.name}</div>
                <div className="text-xs text-neutral-500">{drawerSource.engine} · {drawerSource.host}:{drawerSource.port}/{drawerSource.database}</div>
              </div>
              <button onClick={() => setDrawerSource(null)} className="p-1.5 rounded-md hover:bg-neutral-100">
                <X className="h-4 w-4 text-neutral-500" />
              </button>
            </div>
            <div className="p-5 flex-1 overflow-y-auto space-y-5">
              <div>
                <div className="text-[11px] font-semibold uppercase tracking-wider text-neutral-400 mb-2">Tables découvertes</div>
                <div className="flex flex-wrap gap-2">
                  {drawerTables.map((t) => (
                    <button
                      key={`${t.schema}.${t.name}`}
                      onClick={async () => {
                        const res = await amadApi.tableSchema(drawerSource.id, t.schema, t.name);
                        setSelectedTable({ schema: t.schema, name: t.name, cols: res.columns });
                      }}
                      className="px-3 py-1.5 rounded-md border border-neutral-300 text-xs font-medium hover:bg-neutral-50"
                    >
                      {t.schema}.{t.name} ({t.estimatedRows.toLocaleString()} lignes)
                    </button>
                  ))}
                </div>
              </div>
              {selectedTable && (
                <div>
                  <div className="text-[11px] font-semibold uppercase tracking-wider text-neutral-400 mb-2">
                    Schéma · {selectedTable.schema}.{selectedTable.name}
                  </div>
                  <table className="w-full text-xs text-left border border-neutral-200 rounded-md overflow-hidden">
                    <thead className="bg-neutral-50 text-neutral-500 border-b border-neutral-200">
                      <tr><th className="p-2">#</th><th className="p-2">Colonne</th><th className="p-2">Type</th><th className="p-2">Nullable</th></tr>
                    </thead>
                    <tbody className="divide-y divide-neutral-100">
                      {selectedTable.cols.map((c) => (
                        <tr key={c.ordinal}>
                          <td className="p-2 text-neutral-400">{c.ordinal}</td>
                          <td className="p-2 font-mono text-neutral-900">{c.name}</td>
                          <td className="p-2 text-neutral-600">{c.dataType}</td>
                          <td className="p-2 text-neutral-500">{c.nullable ? "YES" : "NO"}</td>
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
