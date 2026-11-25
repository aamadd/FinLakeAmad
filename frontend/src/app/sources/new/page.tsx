"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AlertTriangle, CheckCircle2, Loader2, ShieldAlert, Zap } from "lucide-react";
import { amadApi, type ProbeResponse } from "@/lib/amadApi";

export default function NewSourcePage() {
  const router = useRouter();
  const [engine, setEngine] = useState<"MSSQL" | "POSTGRES">("MSSQL");
  const [id, setId] = useState("amad-custom-source");
  const [name, setName] = useState("Nouvelle source FinLakeAmad");
  const [host, setHost] = useState("amad-mssql");
  const [port, setPort] = useState(1433);
  const [database, setDatabase] = useState("amad_source");
  const [username, setUsername] = useState("sa");
  const [password, setPassword] = useState("");
  const [tlsEnabled, setTlsEnabled] = useState(false);

  const [testing, setTesting] = useState(false);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [probe, setProbe] = useState<ProbeResponse | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveErr, setSaveErr] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!testing) return;
    const t0 = Date.now();
    const timer = setInterval(() => setElapsedSec(Number(((Date.now() - t0) / 1000).toFixed(1))), 100);
    return () => clearInterval(timer);
  }, [testing]);

  const applyDemoPreset = (kind: "MSSQL" | "POSTGRES") => {
    setProbe(null);
    if (kind === "MSSQL") {
      setEngine("MSSQL");
      setId("amad-mssql-demo");
      setName("Démo SQL Server 2022");
      setHost("amad-mssql");
      setPort(1433);
      setDatabase("amad_source");
      setUsername("sa");
    } else {
      setEngine("POSTGRES");
      setId("amad-pg-demo");
      setName("Démo PostgreSQL 16");
      setHost("amad-postgres");
      setPort(5432);
      setDatabase("amad_source");
      setUsername("amad_source");
    }
  };

  const runProbe = async () => {
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setTesting(true);
    setElapsedSec(0);
    setProbe(null);
    try {
      const res = await amadApi.probeSource(
        { engine, host, port: Number(port), database, username, password, tlsEnabled },
        ctrl.signal
      );
      setProbe(res);
    } catch (err) {
      if ((err as Error).name !== "AbortError") {
        setProbe({
          connected: false,
          engine,
          serverVersion: null,
          steps: [{ step: 1, name: "Joindre le serveur", status: "FAILED", durationMs: 0, detail: String(err) }],
          tables: [],
          errorHint: "Vérifiez l'accessibilité du backend et les paramètres réseau.",
        });
      }
    } finally {
      setTesting(false);
    }
  };

  const saveSource = async () => {
    setSaving(true);
    setSaveErr(null);
    try {
      await amadApi.createSource({ id, name, engine, host, port: Number(port), database, username, password, tlsEnabled });
      router.push("/sources");
    } catch (err) {
      setSaveErr(String(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-zinc-900">Ajouter une connexion</h1>
          <p className="text-sm text-zinc-500">Testez la connectivité en 4 étapes avant d&apos;enregistrer la source.</p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => applyDemoPreset("MSSQL")} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-zinc-200 bg-white hover:bg-zinc-50 text-xs font-medium">
            <Zap className="h-3.5 w-3.5 text-amber-500" /> Démo SQL Server 2022
          </button>
          <button onClick={() => applyDemoPreset("POSTGRES")} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-zinc-200 bg-white hover:bg-zinc-50 text-xs font-medium">
            <Zap className="h-3.5 w-3.5 text-amber-500" /> Démo PostgreSQL 16
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left: 4-section Wireframe Form */}
        <div className="lg:col-span-7 bg-white border border-zinc-200 rounded-xl p-6 space-y-6">
          <section className="space-y-3">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">1. Connexion</h2>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-zinc-700 mb-1">Moteur</label>
                <select value={engine} onChange={(e) => setEngine(e.target.value as "MSSQL" | "POSTGRES")} className="w-full border border-zinc-200 rounded-lg px-3 py-2 text-sm">
                  <option value="MSSQL">Microsoft SQL Server</option>
                  <option value="POSTGRES">PostgreSQL</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-700 mb-1">Identifiant</label>
                <input value={id} onChange={(e) => setId(e.target.value)} className="w-full border border-zinc-200 rounded-lg px-3 py-2 text-sm" />
              </div>
            </div>
            <div>
              <label className="block text-xs font-medium text-zinc-700 mb-1">Nom d&apos;affichage</label>
              <input value={name} onChange={(e) => setName(e.target.value)} className="w-full border border-zinc-200 rounded-lg px-3 py-2 text-sm" />
            </div>
          </section>

          <section className="space-y-3 pt-4 border-t border-zinc-100">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">2. Serveur</h2>
            <div className="grid grid-cols-3 gap-3">
              <div className="col-span-2">
                <label className="block text-xs font-medium text-zinc-700 mb-1">Hôte</label>
                <input value={host} onChange={(e) => setHost(e.target.value)} className="w-full border border-zinc-200 rounded-lg px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-700 mb-1">Port</label>
                <input type="number" value={port} onChange={(e) => setPort(Number(e.target.value))} className="w-full border border-zinc-200 rounded-lg px-3 py-2 text-sm" />
              </div>
            </div>
            <div>
              <label className="block text-xs font-medium text-zinc-700 mb-1">Base de données</label>
              <input value={database} onChange={(e) => setDatabase(e.target.value)} className="w-full border border-zinc-200 rounded-lg px-3 py-2 text-sm" />
            </div>
          </section>

          <section className="space-y-3 pt-4 border-t border-zinc-100">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">3. Authentification</h2>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-zinc-700 mb-1">Utilisateur</label>
                <input value={username} onChange={(e) => setUsername(e.target.value)} className="w-full border border-zinc-200 rounded-lg px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-700 mb-1">Mot de passe</label>
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" className="w-full border border-zinc-200 rounded-lg px-3 py-2 text-sm" />
              </div>
            </div>
          </section>

          <section className="pt-4 border-t border-zinc-100 flex items-center justify-between">
            <div>
              <div className="text-xs font-semibold uppercase tracking-wider text-zinc-400">4. Paramètres avancés</div>
              <div className="text-xs text-zinc-500">Chiffrement TLS/SSL</div>
            </div>
            <input type="checkbox" checked={tlsEnabled} onChange={(e) => setTlsEnabled(e.target.checked)} className="h-4 w-4" />
          </section>

          {saveErr && <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-xs text-rose-700">{saveErr}</div>}

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-zinc-100">
            <button onClick={runProbe} disabled={testing} className="px-4 py-2 rounded-lg border border-zinc-300 bg-white hover:bg-zinc-50 text-sm font-medium">
              Tester la connexion
            </button>
            <button onClick={saveSource} disabled={saving} className="px-4 py-2 rounded-lg bg-[#18181b] hover:bg-zinc-800 text-white text-sm font-medium disabled:opacity-50">
              {saving ? "Enregistrement..." : "Enregistrer la connexion"}
            </button>
          </div>
        </div>

        {/* Right: Interactive 4-Step Live Tester (Wireframe Pages 2-5) */}
        <div className="lg:col-span-5 bg-white border border-zinc-200 rounded-xl p-6 space-y-5 h-fit">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold text-zinc-900">Testeur en direct</h2>
            {testing && (
              <span className="text-xs font-mono text-zinc-500">{elapsedSec.toFixed(1)} s</span>
            )}
          </div>

          {!testing && !probe && (
            <div className="py-10 text-center space-y-2 border border-dashed border-zinc-200 rounded-lg">
              <div className="text-sm font-medium text-zinc-700">Pas encore testée</div>
              <p className="text-xs text-zinc-500">Cliquez sur « Tester la connexion » pour exécuter la sonde en 4 étapes.</p>
            </div>
          )}

          {testing && (
            <div className="py-8 text-center space-y-3 border border-zinc-200 rounded-lg bg-zinc-50">
              <Loader2 className="h-6 w-6 animate-spin text-zinc-700 mx-auto" />
              <div className="text-sm font-medium text-zinc-800">Test de connexion en cours...</div>
              <button onClick={() => abortRef.current?.abort()} className="px-3 py-1 rounded border border-zinc-300 text-xs text-zinc-600 hover:bg-white">
                Annuler le test
              </button>
            </div>
          )}

          {probe && (
            <div className="space-y-4">
              {probe.connected ? (
                <div className="p-3 rounded-lg bg-emerald-50 border border-emerald-200 flex items-center gap-2.5 text-emerald-800 text-xs">
                  <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
                  <div>
                    <div className="font-semibold">Connexion réussie</div>
                    <div>{probe.serverVersion}</div>
                  </div>
                </div>
              ) : (
                <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 flex items-start gap-2.5 text-rose-800 text-xs">
                  <ShieldAlert className="h-4 w-4 text-rose-600 shrink-0 mt-0.5" />
                  <div>
                    <div className="font-semibold">Échec de connexion</div>
                    <div>{probe.errorHint}</div>
                  </div>
                </div>
              )}

              <div className="space-y-2">
                {probe.steps.map((st) => (
                  <div key={st.step} className="p-2.5 rounded-lg border border-zinc-200 flex items-center justify-between text-xs">
                    <div>
                      <div className="font-medium text-zinc-900">{st.step}. {st.name}</div>
                      <div className="text-zinc-500">{st.detail}</div>
                    </div>
                    <span className={`font-semibold ${st.status === "PASSED" ? "text-emerald-600" : "text-rose-600"}`}>
                      {st.status === "PASSED" ? `✓ ${st.durationMs} ms` : "✗ Échec"}
                    </span>
                  </div>
                ))}
              </div>

              {probe.connected && probe.tables.length > 0 && (
                <div className="p-3 rounded-lg bg-zinc-50 border border-zinc-200 space-y-1.5">
                  <div className="text-xs font-semibold text-zinc-700">Preuve de connexion active</div>
                  {probe.tables.map((t) => (
                    <div key={`${t.schema}.${t.name}`} className="text-xs font-mono text-zinc-600 flex justify-between">
                      <span>{t.schema}.{t.name}</span>
                      <span>{t.estimatedRows.toLocaleString()} lignes</span>
                    </div>
                  ))}
                </div>
              )}

              {!probe.connected && (
                <div className="p-3 rounded-lg bg-amber-50 border border-amber-200 text-xs text-amber-900 space-y-1">
                  <div className="font-semibold flex items-center gap-1.5"><AlertTriangle className="h-3.5 w-3.5" /> À vérifier</div>
                  <ul className="list-disc pl-4 space-y-0.5">
                    <li>Le conteneur Docker cible est démarré et healthy.</li>
                    <li>L&apos;hôte (`amad-mssql` / `amad-postgres`) et le port sont corrects.</li>
                    <li>L&apos;utilisateur et le mot de passe correspondent au fichier `.env`.</li>
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
