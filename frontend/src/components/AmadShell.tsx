"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Activity, Database, Layers, Play } from "lucide-react";
import type { ReactNode } from "react";

const NAV = [
  { href: "/", label: "Overview", icon: Activity },
  { href: "/sources", label: "Sources", icon: Database },
  { href: "/jobs", label: "Jobs", icon: Play },
  { href: "/analytics", label: "Trino SQL", icon: Layers },
];

export function AmadShell({ children }: { children: ReactNode }) {
  const path = usePathname();
  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b border-slate-800 bg-slate-950/80 px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="h-2.5 w-2.5 rounded-full bg-emerald-400" />
          <span className="font-semibold tracking-tight text-white">FinLakeAmad</span>
          <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">amad_lake</span>
        </div>
        <nav className="flex items-center gap-2">
          {NAV.map(({ href, label, icon: Icon }) => {
            const active = path === href;
            return (
              <Link
                key={href}
                href={href}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm transition ${
                  active
                    ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30"
                    : "text-slate-400 hover:text-white hover:bg-slate-800/60"
                }`}
              >
                <Icon className="w-4 h-4" />
                {label}
              </Link>
            );
          })}
        </nav>
      </header>
      <main className="flex-1 max-w-6xl w-full mx-auto px-6 py-8">{children}</main>
    </div>
  );
}
